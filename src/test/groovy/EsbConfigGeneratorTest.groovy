/**
 * <summary>
 *
 * <Detailed description>
 */

class EsbConfigGeneratorTest extends GroovyTestCase {

  EsbConfigGenerator generator = new EsbConfigGenerator()
  def site = 'sofia.elex.be'
  def node = 'esb-a'

  void setUp() {

  }

  void testCreateBroker() {
    def broker = generator.createBroker(site, node)

    assertEquals("Broker name is wrong : ", 'brokerA', broker.brokerName)
    assertEquals("Host name is wrong : ", 'esb-a.sofia.elex.be', broker.hostName)
    assertEquals("Stomp port is wrong : ", 61501, broker.stompPort)
    assertEquals("Openwire port is wrong : ", 61601, broker.openwirePort)
    assertEquals("Wrong number of peers : ", 4, broker.peerBrokers.size())
    assertEquals("Wrong slave hostname : ", "esb-b.sofia.elex.be", broker.slaveHostName)
  }

  void testLocalPeers() {
    List<PeerBroker> localPeers = generator.getLocalPeers(site, node)

    assertEquals("Wrong number of peers", 1, localPeers.size())
    assertEquals("Wrong peer hostname",'esb-b.sofia.elex.be', localPeers[0].peerHostName)
    assertEquals("Wrong peer port",61602, localPeers[0].peerPort)
  }

  void testRemotePeers() {
    List<PeerBroker> remotePeers = generator.getRemotePeers(site, node)

    assertEquals("Wrong number of peers", 3, remotePeers.size())
    assertEquals("Wrong peer hostname",'esb-a.colo.elex.be', remotePeers[0].peerHostName)
    assertEquals("Wrong peer hostname",'esb-a.erfurt.elex.be', remotePeers[1].peerHostName)
    assertEquals("Wrong peer hostname",'esb-a.sensors.elex.be', remotePeers[2].peerHostName)
    for(peer in remotePeers) {
      assertEquals("Wrong peer port",61601, peer.peerPort)
    }
  }

}
