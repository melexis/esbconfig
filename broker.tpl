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
            <networkConnector name="{{site}}" uri="static:failover:({{failoverBrokers}})">
            </networkConnector>
	    {{/peerBrokers}}
        </networkConnectors>

        <!-- The transport connectors ActiveMQ will listen to -->
        <transportConnectors>
            <transportConnector name="openwire" uri="tcp://{{hostName}}:{{openwirePort}}"/>
            <transportConnector name="stomp" uri="stomp://{{hostName}}:{{stompPort}}"/>
        </transportConnectors>
    
        <persistenceAdapter>
            <jdbcPersistenceAdapter dataDirectory="activemq-data" dataSource="#activemq-ds">
                 <databaseLocker>
                     <database-locker queryTimeout="-1" />
                 </databaseLocker>
            </jdbcPersistenceAdapter>
        </persistenceAdapter>

    </broker>

    <bean id="activemq-ds" class="org.postgresql.ds.PGPoolingDataSource">
        <property name="serverName" value="postgresql{{prefix}}.colo.elex.be"/>
        <property name="databaseName" value="activemq_{{siteName}}"/>
        <property name="portNumber" value="5432"/>
        <property name="user" value="activemq"/>
        <property name="password" value="{{dsPassword}}"/>
        <property name="initialConnections" value="1"/>
        <property name="maxConnections" value="10"/>
    </bean>

</blueprint>
