# Deployment of SolidityNode and FullNode on the same host

Create separate directories for fullnode and soliditynode
```
    /deploy/fullnode
    /deploy/soliditynode
```

Create two folders for fullnode and soliditynode.

Clone our latest master branch of https://github.com/MidasCore/java-mcashchain and extract it to
```      
    /deploy/java-mcashchain 
```

Make sure you have the proper dependencies.

* JDK 1.8 (JDK 1.9+ is not supported yet)
* On Linux Ubuntu system (e.g. Ubuntu 16.04.4 LTS), ensure that the machine has [__Oracle JDK 8__](https://www.digitalocean.com/community/tutorials/how-to-install-java-with-apt-get-on-ubuntu-16-04), instead of having __Open JDK 8__ in the system. If you are building the source code by using __Open JDK 8__, you will get [__Build Failed__](https://github.com/MidasCore/java-mcashchain/issues/337) result.
* Open **UDP** ports for connection to the network
* **MINIMUM** 2 CPU Cores

## Deployment guide

  1. Build the java-mcashchain project
```
    cd /deploy/java-mcashchain 
    ./gradlew build
```

  2. Copy the FullNode.jar and SolidityNode.jar along with config files into the respective directories.
```
    download your needed config file from https://github.com/MidasCore/java-mcashchain/tree/master/src/main/resources.
    config.conf is the config for mainnet, and config-testnet.conf is the config for testnet.
    please rename the config file to `config.conf` and use this config.conf to start fullnode and soliditynode.

    cp build/libs/FullNode.jar ../fullnode
    cp build/libs/SolidityNode.jar ../soliditynode
```

  3. You can now run your Fullnode using the following command：
```
      java -jar FullNode.jar -c config.conf // make sure that you download the latest version of config.conf from https://github.com/MidasCore/java-mcashchain/releases
```

  4. Configure the SolidityNode configuration file. You'll need to edit `config.conf` to connect to your local `FullNode`. Change  `trustNode` in `node` to local `127.0.0.1:60061`, which is the default rpc port. Set `listen.port` to any number within the range of 1024-65535. Please don't use any ports between 0-1024 since you'll most likely hit conflicts with other system services. Also change `rpc port` to `60062` or something to avoid conflicts. **Please forward the UDP port 18888 for FullNode.**
```
    rpc {
      port = 60062
    }
```

  5. You can now run your SolidityNode using the following command：
```        
    java -jar SolidityNode.jar -c config.conf // make sure that you download the latest version of config.conf from https://github.com/MidasCore/java-mcashchain/releases
```

# Logging and network connection verification

Logs for both nodes are located in `/deploy/\*/logs/mcashchain.log`. Use `tail -f /logs/mcashchain.log/` to follow along with the block syncing.

You should see something similar to this in your logs for block synchronization:

## FullNode

      08:00:30.192 INFO  [ClientWorker-3] [i.m.c.n.s.AdvService](AdvService.java:126) Ready to broadcast block Num:378817,ID:000000000005c7c1994715cdc94410877391045061f745303fe48f30e49730cf
      08:00:30.195 INFO  [ClientWorker-0] [net](MessageQueue.java:98) Receive from /138.197.7.138:11399, type: INVENTORY
invType: BLOCK, size: 1, First hash: 000000000005c7c1994715cdc94410877391045061f745303fe48f30e49730cf


## SolidityNode

      20:06:10.866 INFO  [Thread-14] [app](SolidityNode.java:142) Get last remote solid blockNum: 0, remoteBlockNum: 0, cost: 4
      
# Stop node gracefully
Create file stop.sh，use kill -15 to close java-mcashchain.jar（or FullNode.jar、SolidityNode.jar）.
You need to modify pid=`ps -ef |grep java-mcashchain.jar |grep -v grep |awk '{print $2}'` to find the correct pid.
```
#!/bin/bash
while true; do
  pid=`ps -ef |grep java-mcashchain.jar |grep -v grep |awk '{print $2}'`
  if [ -n "$pid" ]; then
    kill -15 $pid
    echo "The java-mcashchain process is exiting, it may take some time, forcing the exit may cause damage to the database, please wait patiently..."
    sleep 1
  else
    echo "java-mcashchain killed successfully!"
    break
  fi
done
```