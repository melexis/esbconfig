@GrabResolver(name='nexus',
              root='http://nexus.colo.elex.be:8081/nexus/content/groups/public')
@Grab(group='com.samskivert', module='jmustache', version='1.3')
import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template

@Grab(group='org.yaml', module='snakeyaml', version='1.8')
import org.yaml.snakeyaml.Yaml

import java.io.File

/**
 * Generate the configuration files from templates for the ESB Grid
 *
 * Generating the files for the grid is complicated, error prone and very
 * repetitive. So we automate it.
 */


class Broker {
  static def SITE_MAPPING = [ 'sensors.elex.be': 'ieper'
                            , 'sofia.elex.be': 'sofia'
                            , 'erfurt.elex.be': 'erfurt'
                            , 'colo.elex.be': 'diegem' ]

  def hostName
  def brokerName
  int openwirePort = 61601
  int stompPort = 61501
  def peerBrokers
  def failoverPeerBrokers
  def slaveHostName
  def env
  def site
  def dsPassword
  def prefix

  def siteName() {
    SITE_MAPPING.get(site)
  }

  def String toString() {
    """{ hostName: ${hostName},
         brokerName: ${brokerName},
         openwirePort: ${openwirePort},
         stompPort: ${stompPort},
         peerBrokers: ${peerBrokers},
         failoverPeerBrokers: ${failoverPeerBrokers},
         slaveHostName: ${slaveHostName},
         env: ${env},
         site: ${site},
         prefix: ${prefix},
         dsPassword: ${dsPassword}
       }"""
  }
}

class PeerBroker {
  def peerHostName;
  def peerPort;

  PeerBroker(peerHostName, peerPort) {
    this.peerHostName = peerHostName
    this.peerPort = peerPort
  }

  String toString() {
    """{ peerHostName: ${peerHostName},
         peerPort: ${peerPort}
       }"""
  }
}

class Peer {
  def site
  def brokers

  Peer(site, brokers) {
    this.site = site
    this.brokers = brokers
  }

  def failoverBrokers() {
    this.brokers.collect { "tcp://${it.peerHostName}:${it.peerPort}" }.join(',')
  }

  String toString() {
    """{ site: ${site},
         brokers: ${brokers}
       }"""
  }
}

class Environment {
  def prefix
  def datasourcePassword

  Environment(prefix, datasourcePassword) {
    this.prefix = prefix
    this.datasourcePassword = datasourcePassword
  }

  def String toString() {
    """{ prefix: ${prefix}, datasourcePassword: ${datasourcePassword} }"""
  }
}

class EsbConfigGenerator {

  def sites = []
  def nodes = []
  def targetDir
  def envs = []
  def openwirePort = 61601

  public def brokers = []

  EsbConfigGenerator() {
    def configFileName = 'config.yaml'
    def configStream = getClass().getClassLoader().getResourceAsStream(configFileName)
    def config = new Yaml().load(configStream);

    sites = config['sites']
    nodes = config['nodes']

    brokers = config['brokers'].collectEntries { k, b ->
        b['envs'] = b['envs'].values().collect { env ->
            def prefix = env['prefix'] == null? '': env['prefix']
            new Environment(prefix, env['datasourcePassword'])
        }
        [k, b]
    }

    targetDir = "target/"
  }

  public createBrokers() {
    for (b in brokers) {
      brokertype = b.key
      template = b.value['template']
      openwirePort = b.value['openwirePort']
      stompPort = b.value['stompPort']
      for (env in b.value['envs']) {
        for (site in sites) {
          for (node in nodes) {
            def broker = createBroker(site, node, env, template, openwirePort, stompPort, b.value['brokername'])
            brokers.add(broker)
          }
        }
      }
    }
  }

  public generateConfigFiles() {
    println("Brokers: ${brokers}")
    for (b in brokers) {
      def templateName = b.value['template']
      def template = createTemplateFromResource(templateName)
      def brokername = b.value['brokername']
      def openwirePort = b.value['openwirePort']
      def stompPort = b.value['stompPort']

      println("Broker: ${b}")
      for (env in b.value['envs']) {
        for (site in sites) {
	      for (node in nodes) {
              println "ENV: ${env}"
	          def broker = createBroker(site.value['domain'], node, env, template, openwirePort, stompPort, b.value['brokername'])
              println "Broker: ${broker.siteName()}"
	          generateConfigFile(b.key, template, broker, brokername)
          }
	    }
      }
    }
  }
  private def generateConfigFile(String variant, Template template, Broker broker, String brokername) {
    def destination = new File(targetDir)
    if (!destination.exists()) { destination.mkdir() }
    def filename = "target/${broker.hostName}-${variant}.xml"
    println("Writing to file ${filename}")
    new File(filename).withPrintWriter { writer ->
      template.execute(broker, writer)
    }
  }

  private Template createTemplateFromResource(String templateName) {
    def stream = getClass().getClassLoader().getResourceAsStream(templateName)
    def rdr = new InputStreamReader(stream)
    def template = Mustache.compiler().compile(rdr)
    return template
  }

  def createBroker(String site, String node, 
                  Environment env, Template template, 
                  openwirePort, stompPort, brokerName) {
    def broker = new Broker()
    def prefix = env.prefix

    println "ENV 2: ${env} ${env.prefix} " + prefix.getClass().getName()

    broker.hostName = "${node}${env.prefix}.${site}"

    println "HOSTNAME : ${broker.hostName}"

    broker.brokerName = brokerName
    broker.peerBrokers = getRemotePeers(site, prefix)
    broker.slaveHostName = getSlaveHostName(node, site, prefix)
    broker.env = env
    broker.site = site
    broker.dsPassword = env.datasourcePassword
    broker.prefix = env.prefix
    broker.openwirePort = openwirePort
    broker.stompPort = stompPort

    return broker
  }

  private GString getSlaveHostName(String node, String site, String env) {
    def slaveIndex = (nodes.indexOf(node) + 1) % nodes.size()
    def slaveHostName = "${nodes[slaveIndex]}${env}.${site}"
    return slaveHostName
  }

  private String getNodeCode(String node) {
    return node[4].toUpperCase()
  }

  int getNodeOffset(String node) {
    return getNodeCode(node).charAt(0) - 64
  }

  def List<Peer> getRemotePeers(String site, String prefix) {
    def remotePeers = []
    def remoteSites = this.sites.findAll { it != site }
    remoteSites.collect { remoteSite ->
            def domain = remoteSite.getValue()['domain']
            def failoverPeers = this.nodes.collect { node ->
                def peerBroker = new PeerBroker("${node}${prefix}.${domain}", openwirePort)
                peerBroker
            }
            new Peer(domain, failoverPeers)
    }
  }

}

def main() {
  println("running")
  EsbConfigGenerator generator = new EsbConfigGenerator()
  generator.generateConfigFiles()
}

main()
