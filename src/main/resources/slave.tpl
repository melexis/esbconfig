<broker masterConnectorURI="tcp://{{hostName}}:{{openwirePort}}" shutdownOnMasterFailure="false">
   <persistenceAdapter>
      <journaledJDBC journalLogFiles="5" dataDirectory="${activemq.base}/data/{{brokerName}}" />
   </persistenceAdapter>

   <transportConnectors>
	  <transportConnector name="openwire" uri="tcp://{{slaveHostName}}:{{openwirePort}}"/>
	  <transportConnector name="stomp" uri="tcp://{{slaveHostName}}:{{stompPort}}"/>
   </transportConnectors>
</broker>