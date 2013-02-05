<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0"
           xmlns:cm="http://aries.apache.org/blueprint/xmlns/blueprint-cm/v1.0.0"
           xmlns:ext="http://aries.apache.org/blueprint/xmlns/blueprint-ext/v1.0.0"
           xmlns:amq="http://activemq.apache.org/schema/core">

    <!-- Allows us to use system properties as variables in this configuration file -->
    <ext:property-placeholder />

    <broker xmlns="http://activemq.apache.org/schema/core" brokerName="{{brokerName}}" dataDirectory="${karaf.data}/activemq/{{brokerName}}" useShutdownHook="false">

       <!--
            For better performances use VM cursor and small memory limit.
            For more information, see:

            http://activemq.apache.org/message-cursors.html

            Also, if your producer is "hanging", it's probably due to producer flow control.
            For more information, see:
            http://activemq.apache.org/producer-flow-control.html
        -->

        <destinationPolicy>
            <policyMap>
              <policyEntries>
                <policyEntry topic=">" producerFlowControl="true" memoryLimit="1mb">
                  <pendingSubscriberPolicy>
                    <vmCursor />
                  </pendingSubscriberPolicy>
                </policyEntry>
                <policyEntry queue=">" producerFlowControl="true" memoryLimit="1mb">
                  <!-- Use VM cursor for better latency
                       For more information, see:

                       http://activemq.apache.org/message-cursors.html

                  <pendingQueuePolicy>
                    <vmQueueCursor/>
                  </pendingQueuePolicy>
                  -->
                </policyEntry>
              </policyEntries>
            </policyMap>
        </destinationPolicy>

        <!-- Use the following to configure how ActiveMQ is exposed in JMX -->
        <managementContext>
            <managementContext createConnector="false"/>
        </managementContext>

        <!-- Connection to peers in network of brokers -->
        <networkConnectors>
            {{#peerBrokers}}
            <networkConnector name="{{peerHostName}}" uri="static:(tcp://{{peerHostName}}:{{peerPort}})">
	      	<excludedDestinations>
   	  	  <queue physicalName="Consumer.*.VirtualTopic.>"/>
      		</excludedDestinations>
            </networkConnector>
	    {{/peerBrokers}}
        </networkConnectors>


        <!--
            Configure message persistence for the broker. The default persistence
            mechanism is the KahaDB store (identified by the kahaDB tag).
            For more information, see:

            http://activemq.apache.org/persistence.html
        -->
        <persistenceAdapter>
            <kahaDB directory="${karaf.data}/activemq/{{brokerName}}/kahadb"/>
        </persistenceAdapter>

       <!--
            The systemUsage controls the maximum amount of space the broker will
            use before slowing down producers. For more information, see:

            http://activemq.apache.org/producer-flow-control.html

        <systemUsage>
            <systemUsage>
                <memoryUsage>
                    <memoryUsage limit="20 mb"/>
                </memoryUsage>
                <storeUsage>
                    <storeUsage limit="1 gb" name="foo"/>
                </storeUsage>
                <tempUsage>
                    <tempUsage limit="100 mb"/>
                </tempUsage>
            </systemUsage>
        </systemUsage>
        -->

        <!-- The transport connectors ActiveMQ will listen to -->
        <transportConnectors>
            <transportConnector name="openwire" uri="tcp://{{hostName}}:{{openwirePort}}"/>
            <transportConnector name="stomp" uri="stomp://{{hostName}}:{{stompPort}}"/>
        </transportConnectors>

    </broker>

    <bean id="activemqConnectionFactory" class="org.apache.activemq.ActiveMQConnectionFactory">

        <property name="brokerURL" value="tcp://{{hostName}}:{{openwirePort}}" />
    </bean>

    <bean id="pooledConnectionFactory" class="org.apache.activemq.pool.PooledConnectionFactory">
        <property name="maxConnections" value="8" />
        <property name="connectionFactory" ref="activemqConnectionFactory" />
    </bean>

    <bean id="resourceManager" class="org.apache.activemq.pool.ActiveMQResourceManager" init-method="recoverResource">
          <property name="transactionManager" ref="transactionManager" />
          <property name="connectionFactory" ref="activemqConnectionFactory" />
          <property name="resourceName" value="activemq.{{brokerName}}" />
    </bean>

    <reference id="transactionManager" interface="javax.transaction.TransactionManager" />

    <service ref="pooledConnectionFactory" interface="javax.jms.ConnectionFactory">
        <service-properties>
            <entry key="name" value="{{hostName}}"/>
        </service-properties>
    </service>

</blueprint>

