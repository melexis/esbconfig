ESB Configuration Generator
===========================

We use at melexis a communication grid over the sites.


This grid is actually composed of 2 fully meshed networks of brokers which have
a connection on every site to the local member of the other network. Every site
has two nodes with a backup configured on the other node.

For N sites this means N + 2 - 2 connections per node.

However this needs a lot of configuration files which are systematically filled
in. These configuration files are as DRY as the ocean. This is a magnet for
configuration errors if this is to be done manually.

Since, especially during testing phase, we expect the files to be changed a lot
we need a practical way to regenerate and distribute them over the grid.

Our cfengine based system works well to distribute the files (albeit rather
slow).

This script generates the configuration files for the master broker and slave
broker on every node. The script is slightly ;ore genral in that it allows to
create M networks of N sites with fully meshed interconnects between the network
on every site leading to M + N - 2 connections per node.

Usage
-----

The script runs using groovy. 

    $> groovy configgenerator.groovy

This will run the compiled groovy code and generate the configuration files in
the target directory.

Modifying
---------

The templates use **Mustache** as a templating language because it is easy to
read and write and makes adding business logic to templates near impossible.
The bits between {{ and }} are replaced by the passed object in the script, in
this case a Broker object from the groovy script.

There are 2 templates :

 - **src/main/resources/broker.tpl** for the master config of a node
 - **src/main/resources/slace.tpl** for the slave running on this node

 The configuration of the grid is in **src/main/resources/config.yaml. This is
 a YAML file which contains the sites (as network part of the domain name). The
 format is self explanatory, but more details can be found on the
 [YAML homepage](http://yaml.org/).

