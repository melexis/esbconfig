// @Grab(group='com.samskivert', module='jmustache', version='1.3')

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import org.yaml.snakeyaml.Yaml

/**
 * Generate the configuration files from templates for the ESB Grid
 *
 * Generating the files for the grid is complicated, error prone and very
 * repetitive. So we automate it.
 */


class Broker {
  def hostName;
  def brokerName;
  int openwirePort;
  int stompPort;
  def peerBrokers = [];
  def slaveHostName;
}

class PeerBroker {
  def peerHostName;
  def peerPort;
}

class EsbConfigGenerator {

  def sites = []

  def nodes = []
  Template brokerTemplate
  Template slaveTemplate


  EsbConfigGenerator() {
    def configFileName = 'config.yaml'
    def configStream = getClass().getClassLoader().getResourceAsStream(configFileName)
    def config = new Yaml().load(configStream);
    sites = config['sites']
    nodes = config['nodes']

    brokerTemplate = createTemplateFromResource('broker.tpl')
    slaveTemplate = createTemplateFromResource('slave.tpl')
  }

  static void main(String[] args) {
    EsbConfigGenerator generator = new EsbConfigGenerator()
    generator.generateConfigFiles()
  }

  public generateConfigFiles() {

    for (site in sites) {
      for (node in nodes) {
        def broker = createBroker(site, node)

        createBrokerConfig(broker);
        createSlaveConfig(broker);

      }
    }
  }

  void createBrokerConfig(Broker broker) {
    def filename = "target/${broker.hostName}-broker.xml"
    new File(filename).withPrintWriter { writer ->
            brokerTemplate.execute(broker,writer)
    }
  }

  void createSlaveConfig(Broker broker) {
    def filename = "target/${broker.hostName}-slave.xml"
    new File(filename).withPrintWriter { writer ->
            slaveTemplate.execute(broker,writer)
    }
  }

  private Template createTemplateFromResource(String templateName) {
    def stream = getClass().getClassLoader().getResourceAsStream(templateName)
    def rdr = new InputStreamReader(stream)
    def template = Mustache.compiler().compile(rdr)
    return template
  }

  def createBroker(String site, String node) {
    def broker = new Broker()

    broker.hostName = "${node}.${site}"
    broker.brokerName = "broker" + getNodeCode(node)
    broker.openwirePort = getOpenwirePort(node)
    broker.stompPort = getStompPort(node)
    broker.peerBrokers += getLocalPeers(site, node)
    broker.peerBrokers += getRemotePeers(site, node)
    broker.slaveHostName = getSlaveHostName(node, site)

    return broker
  }

  private GString getSlaveHostName(String node, String site) {
    def slaveIndex = (nodes.indexOf(node) + 1) % nodes.size()
    def slaveHostName = "${nodes[slaveIndex]}.${site}"
    return slaveHostName
  }

  int getStompPort(String node) {
    return 61500 + getNodeOffset(node)
  }

  int getOpenwirePort(String node) {
    return 61600 + getNodeOffset(node)
  }

  private String getNodeCode(String node) {
    return node[-1].toUpperCase()
  }

  int getNodeOffset(String node) {
    return getNodeCode(node).charAt(0) - 64
  }

  def List<PeerBroker> getLocalPeers(String site, String node) {
    def localPeers = []

    for (peer in this.nodes) {
      if (peer != node) {
        PeerBroker peerBroker = new PeerBroker();
        peerBroker.peerHostName = "${peer}.${site}"
        peerBroker.peerPort = getOpenwirePort(peer)
        localPeers.add(peerBroker)
      }
    }
    return localPeers
  }

  def List<PeerBroker> getRemotePeers(String site, String node) {
    def remotePeers = []

    for (peer in this.sites) {
      if (peer != site) {
        PeerBroker peerBroker = new PeerBroker();
        peerBroker.peerHostName = "${node}.${peer}"
        peerBroker.peerPort = getOpenwirePort(node)
        remotePeers.add(peerBroker)
      }
    }
    return remotePeers
  }



}
