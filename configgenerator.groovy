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
}

class Peer {
  def site
  def brokers

  Peer(site, brokers) {
    this.site = site
    this.brokers = brokers
  }

  def failoverBrokers() {
    this.brokers.collect { "${it.peerHostName}:${it.peerPort}" }.join(',')
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
  Template brokerTemplate
  Template slaveTemplate
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
    envs = config['envs'].values().collect { env ->
        def prefix = env['prefix'] == null? '': env['prefix']
        new Environment(prefix, env['datasourcePassword']) 
    }


    brokerTemplate = createTemplateFromResource('broker.tpl')
    targetDir = "target/"
  }

  static void main(String[] args) {
    EsbConfigGenerator generator = new EsbConfigGenerator()
    generator.generateConfigFiles()
  }

  public createBrokers() {
    for (env in envs) {
      for (site in sites) {
        for (node in nodes) {
          def broker = createBroker(site, node, env)
          brokers.add(broker)
        }
      }
    }
  }

  public generateConfigFiles() {
    for (env in envs) {
      for (site in sites) {
	    for (node in nodes) {
	        def broker = createBroker(site, node, env)

	        generateConfigFile("masterslave", brokerTemplate, broker)
	    }
      }
    }
  }
  private def generateConfigFile(variant, Template template, Broker broker) {
    def destination = new File("target")
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

  def createBroker(String site, String node, Environment env) {
    def broker = new Broker()

    broker.hostName = "${node}${env.prefix}.${site}"
    broker.brokerName = "broker" + getNodeCode(node)
    broker.peerBrokers = getRemotePeers(site, env.prefix)
    broker.slaveHostName = getSlaveHostName(node, site, env.prefix)
    broker.env = env
    broker.site = site
    broker.dsPassword = env.datasourcePassword

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

  def List<PeerBroker> getLocalPeers(String site, String node, String env) {
    def localPeers = []

    for (peer in this.nodes) {
      if (peer != node) {
        PeerBroker peerBroker = new PeerBroker();
        peerBroker.peerHostName = "${peer}${env}.${site}"
        peerBroker.peerPort = 61601
        localPeers.add(peerBroker)
      }
    }
    return localPeers
  }

  def List<Peer> getRemotePeers(String site, String env) {
    def remotePeers = []

    def remoteSites = this.sites.findAll { it != site }
    remoteSites.collect { peer ->
            def failoverPeers = this.nodes.collect { node ->
                def peerBroker = new PeerBroker("${node}${env}.${peer}", openwirePort)
                peerBroker
            }
            new Peer(peer, failoverPeers)
    }
  }

}

def main() {
  println("running")
    EsbConfigGenerator generator = new EsbConfigGenerator()
    generator.generateConfigFiles()
}

main()
