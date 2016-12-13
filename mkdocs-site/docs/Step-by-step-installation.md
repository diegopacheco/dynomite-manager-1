# AWS Deployment

Author: [Diego Pacheco](https://github.com/diegopacheco)

## Install Cassandra for Token Management


### Install java 8
```bash
# Remove java 7
sudo yum remove -y java

# Install basic packages
sudo yum install -y git

# Download and install java 8
wget --no-cookies --no-check-certificate --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/8u45-b14/jdk-8u45-linux-x64.tar.gz"
tar -xzvf jdk-8u45-linux-x64.tar.gz
rm -rf jdk-8u45-linux-x64.tar.gz

# Configure JAVA_HOME
sudo vim ~/.bashrc
```
```bash
alias cls='clear'

export JAVA_HOME=~/jdk1.8.0_45
export JRE_HOME=~/jdk1.8.0_45/jre
export PATH=$PATH:~/jdk1.8.0_45/bin:/~/jdk1.8.0_45/jre/bin
```
```bash
source ~/.bashrc 
java -version
```

### Cassandra 2.x
```bash
wget https://archive.apache.org/dist/cassandra/2.1.9/apache-cassandra-2.1.9-bin.tar.gz
tar -xzvf apache-cassandra-2.1.9-bin.tar.gz
rm -rf apache-cassandra-2.1.9-bin.tar.gz
```

### Configure the Cassandra Cluster

```bash
# your_server_ip - copy the IP
hostname -i
vim ~/apache-cassandra-2.1.9/conf/cassandra.yaml
```
```yaml
cluster_name: 'Test Cluster'
listen_address: your_server_ip
rpc_address: your_server_ip
seed_provider:
  - class_name: org.apache.cassandra.locator.SimpleSeedProvider
    parameters:
         - seeds: "ip1,ip2,...ipN"
endpoint_snitch: GossipingPropertyFileSnitch
```

### Start up the Cassandra cluster 

```bash
# on each Cassandra node... 
cd ~/apache-cassandra-2.1.9
bin/cassandra start
```

### Open EC2 Security Group ports
```bash
7000
9160
9042
```

### Test data replication with `cqlsh` 

```bash
# Connect to any node - you can run: hostname -i to get the IP
apache-cassandra-2.1.9/bin/cqlsh IP
```
```bash
CREATE KEYSPACE CLUSTER_TEST WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 }; 
USE CLUSTER_TEST;
CREATE TABLE TEST ( key text PRIMARY KEY, value text);
INSERT INTO TEST (key,value) VALUES ('1', 'works');
SELECT * from TEST;
```
```bash
# Connect to any other node - you can run: hostname -i to get the IP - check for data replication
apache-cassandra-2.1.9/bin/cqlsh IP
USE CLUSTER_TEST;
SELECT * from TEST;
```

## Create a AWS Role called: dynomite

Goto Identity and Access Management(https://console.aws.amazon.com/iam/home?region=us-west-2#roles) at Roles.<BR> 
Create a new role called: dynomite and add the policies: <BR>
<ul>
   <li>AmazonEC2FullAccess
   <li>AmazonS3FullAccess
   <li>IAMFullAccess
</ul>

## Create S3 bucket

Goto S3(https://console.aws.amazon.com/s3/home?region=us-west-2#) and create bucket called: dynomite-backup.
Goto Properties-> Permissions and them add: Any Authenticated AWS User (List, Upload/Delete/View/Edit).

## Setup Dynomite and Dynomite Manager + Build an AMI

2.1. Create a Amazon Linux AMI Box <br><br>
2.1.1. Select The Instance type: m4.large Or anything you like it. <br><br>
2.1.2. Configure the Instance Details: IAM Roles: IAM role <br><br>
2.1.3. Configure the Instance Details: Monitoring - Mark Enable CloudWatch detailed monitoring <br><br>
2.1.4. Add Storage: 20Gb storare Or anything you like it.  <br><br>
2.1.5. Tag Instance: dynomite_dynomitemanager <br><br>
2.1.6. Configure Security Group: sg_asg_dynomite_florida with rules: <br><br>
Enable TCP: <br>
<ul>
  <li>22
  <li>8080
  <li>8000 - 8100 (for debug)
  <li>8101 - 8102(dynomite) 
  <li>7000 (cassandra) 
  <li>9160 (cassandra) 
  <li>9042 (cassandra) 
</ul>  

This could vary depending of your VPC config because <br>
you might need add several rules dependeing how my ips you need to allow. But this are the ports you need be enable. 

2.1.7. Pick whatever PEM file do you want. Finish the Box creation and SSH to the box. <br>
3. Installing Dynomite, Redis, Dynomite-Manager and Cassandra
 
Let's update the OS first.
```bash
sudo yum update -y
```

Let's setup Java JDK 8(Needed by Dynomite Manager).
```bash
sudo yum remove java -y
sudo wget --no-cookies --no-check-certificate --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/8u45-b14/jdk-8u45-linux-x64.tar.gz"
tar -xzvf jdk-8u45-linux-x64.tar.gz
rm -rf jdk-8u45-linux-x64.tar.gz
```

We also need export JAVA OS env vars. Let's edit /etc/profile and add:
```bash
export JAVA_HOME=/home/ec2-user/jdk1.8.0_45
export JRE_HOME=/home/ec2-user/jdk1.8.0_45/jre
export PATH=$PATH:/home/ec2-user/jdk1.8.0_45/bin:/home/ec2-user/jdk1.8.0_45/jre/bin

alias cls=clear
alias dlog='tail -f -n 2000  /logs/system/dynomite/dynomite.log'
alias dmlog='tail -f -n 2000 /logs/system/dynomite-manager/dynomite-manager.log'
alias rlog='tail -f -n 2000 /var/log/redis_22122.log'
alias clog='tail -f -n 2000 /home/ec2-user/apache-cassandra-2.1.9/logs/system.log'
alias dgrep='ps aux | grep dynomite'
alias rcli='redis-cli -p 8102'
alias dconf='cat /apps/dynomite/conf/dynomite.yml'
```

As you can see we also added some bash alias to make your life easier. :-) <BR> 

We need to source this file in order to have the variables and alias available, do:
```bash
source /etc/profile
```

Let's move to dynomite now. Let's Download and Build it.
```bash
sudo yum install git -y
git clone https://github.com/Netflix/dynomite.git
cd dynomite
sudo yum install -y autoconf automake
sudo yum install -y libtool
sudo yum install -y openssl-devel
autoreconf -fvi
./configure --enable-debug=log
make
```

In order to test Dynomite Installation just run:
```bash
src/dynomite -h
```

You should see something like:
```
[ec2-user@ip-172-31-14-210 dynomite]$ src/dynomite -h
This is dynomite-alloc_msg_leak-130-g68683f0

Usage: dynomite [-?hVdDt] [-v verbosity level] [-o output file]
                  [-c conf file] [-s stats port] [-a stats addr]
                  [-i stats interval] [-p pid file] [-m mbuf size]
                  [-M max alloc messages]

Options:
  -h, --help             : this help
  -V, --version          : show version and exit
  -t, --test-conf        : test configuration for syntax errors and exit
  -g, --gossip           : enable gossip (default: disable)
  -d, --daemonize        : run as a daemon
  -D, --describe-stats   : print stats description and exit
  -v, --verbosity=N            : set logging level (default: 5, min: 0, max: 11)
  -o, --output=S               : set logging file (default: stderr)
  -c, --conf-file=S            : set configuration file (default: conf/dynomite.yml)
  -s, --stats-port=N           : set stats monitoring port (default: 22222)
  -a, --stats-addr=S           : set stats monitoring ip (default: 0.0.0.0)
  -i, --stats-interval=N       : set stats aggregation interval in msec (default: 30000 msec)
  -p, --pid-file=S             : set pid file (default: off)
  -m, --mbuf-size=N            : set size of mbuf chunk in bytes (default: 16384 bytes)
  -M, --max-msgs=N             : set max number of messages to allocate (default: 200000)
  -x, --admin-operation=N      : set size of admin operation (default: 0)

```

Adding the Dynomite config. 
```bash
sudo mkdir -p /apps/dynomite/conf/
sudo vim /apps/dynomite/conf/dynomite.yml
```

With the content:
```yaml
dyn_o_mite:
  dyn_listen: 0.0.0.0:8101
  data_store: 0
  listen: 0.0.0.0:8102
  dyn_seed_provider: florida_provider
  servers:
    - 127.0.0.1:22122:1
  tokens: '1383429731'
  auto_eject_hosts: true
  rack: null
  distribution: vnode
  gos_interval: 10000
  hash: murmur
  preconnect: true
  server_retry_timeout: 30000
  timeout: 5000
  secure_server_option: datacenter
  datacenter: us-west-2
  read_consistency: DC_ONE
  write_consistency: DC_ONE
  pem_key_file: /apps/dynomite/conf/dynomite.pem

```


Let's create a startup script for Dynomite.
```bash
sudo touch /etc/init.d/dynomite
sudo vim /etc/init.d/dynomite
```

With the content:
```bash
#!/bin/bash
# chkconfig: 2345 95 20
# description: This script does some stuff
# processname: dynomite

start() {
   echo "starting dynomite... "
   cd /apps/dynomite/
   sudo bin/dynomite -d -c /apps/dynomite/conf/dynomite.yml -m16384 -M200000 --output=/logs/system/dynomite/dynomite.log &
}

stop() {
   echo "stop"
   PID=`pgrep dynomite`
   if [[ "" !=  "$PID" ]]; then
      echo "killing $PID"
      kill -9 $PID
   fi
}

case "$1" in start)
  start
;;
  stop)
  stop
;;
*)

echo $"Usage: $0 {start|stop}"
RETVAL=1
esac
exit 0

```

Now we need add permissions to execute and put it on the startup of the box.
```bash
sudo chmod +x /etc/init.d/dynomite
sudo chkconfig dynomite off
```


OK. Next step is install Redis 3.X.
```bash
cd ..
sudo yum install -y gcc*
sudo yum install -y tcl
wget http://download.redis.io/releases/redis-3.0.4.tar.gz
tar xzf redis-3.0.4.tar.gz
cd redis-3.0.4
cd deps ; make hiredis jemalloc linenoise lua ; cd ..
make
make test
sudo make install
cd utils ; sudo chmod +x install_server.sh ; sudo ./install_server.sh
```

The last command will start the redis installer you will need anwser the questions as I did it here:
```bash
Welcome to the redis service installer
This script will help you easily set up a running redis server

Please select the redis port for this instance: [6379] 22122
Please select the redis config file name [/etc/redis/22122.conf] /apps/nfredis/conf/redis.conf
Please select the redis log file name [/var/log/redis_22122.log] /var/log/redis_22122.log
Please select the data directory for this instance [/var/lib/redis/22122] /mnt/data/nfredis/
Please select the redis executable path [] /usr/local/bin/redis-server
Selected config:
Port           : 22122
Config file    : /apps/nfredis/conf/redis.conf
Log file       : /var/log/redis_22122.log
Data dir       : /mnt/data/nfredis/
Executable     : /usr/local/bin/redis-server
Cli Executable : /usr/local/bin/redis-cli
Is this ok? Then press ENTER to go on or Ctrl-C to abort.
Copied /tmp/22122.conf => /etc/init.d/redis_22122
Installing service...
Successfully added to chkconfig!
Successfully added to runlevels 345!
Starting Redis server...
Installation successful!
```

After the installation we should test your Redis installation doing this:
```bash
cd ~
rm -rf redis-3.0.4.tar.gz

[ec2-user@ip-172-31-14-210 utils]$ redis-cli
Could not connect to Redis at 127.0.0.1:6379: Connection refused
not connected> 
[ec2-user@ip-172-31-14-210 utils]$ redis-cli -p 22122
127.0.0.1:22122> set k1 redis
OK
127.0.0.1:22122> get k1
"redis"
127.0.0.1:22122> 
```

Since Redis is working we can test Dynomite as Well. Dynomite is RESP compatible let's test ddynomite using the standard<BR> redis client. Redis is on the port 21222 and dynomite on 8102.
```bash
[ec2-user@ip-172-31-20-132 ~]$ redis-cli -p 22122
127.0.0.1:22122> set k1 redis
OK
127.0.0.1:22122> get k1
"redis"
127.0.0.1:22122> 
[ec2-user@ip-172-31-20-132 ~]$ redis-cli -p 22122
127.0.0.1:22122> get k1
"redis"
127.0.0.1:22122> 
[ec2-user@ip-172-31-20-132 ~]$ redis-cli -p 8102
127.0.0.1:8102> get k1
"redis"
127.0.0.1:8102> set k1 dynomite
OK
127.0.0.1:8102> get k1
"dynomite"
127.0.0.1:8102> 
[ec2-user@ip-172-31-20-132 ~]$ redis-cli -p 22122
127.0.0.1:22122> get k1
"dynomite"
127.0.0.1:22122> 
```

We also can check the log using our bash alias dlog.
```bash
     #                                      m                        
  mmm#  m   m  mmmm    mmm   mmmmm  mmm    mm#mm   mmm                
 #   #  \m m/  #   #  #   #  # # #    #      #    #   #               
 #   #   #m#   #   #  #   #  # # #    #      #    #''''               
 \#m##   \#    #   #   #m#   # # #  mm#mm    mm    #mm                
         m/                                
[2016-06-25 18:59:25.557] stats_listen:1294 m 5 listening on '0.0.0.0:22222'
[2016-06-25 18:59:25.557] entropy_key_iv_load:365 Key File name: conf/recon_key.pem - IV File name: conf/recon_iv.pem
[2016-06-25 18:59:25.558] entropy_key_iv_load:420 key loaded: 0123456789012345
[2016-06-25 18:59:25.558] entropy_key_iv_load:428 iv loaded: 0123456789012345
[2016-06-25 18:59:25.558] entropy_listen:329 anti-entropy m 9 listening on '127.0.0.1:8105'
[2016-06-25 18:59:25.558] conn_connect:525 connected to '127.0.0.1:22122:1' on p 12
[2016-06-25 18:59:25.558] proxy_init:124 p 13 listening on '0.0.0.0:8102' in redis pool 'dyn_o_mite'
[2016-06-25 18:59:25.558] dnode_init:108 dyn: p 14 listening on '0.0.0.0:8101' in redis pool 'dyn_o_mite' with 14615920 servers
[2016-06-25 18:59:25.558] preselect_remote_rack_for_replication:1803 my rack index 0
[2016-06-25 18:59:28.756] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33040'
[2016-06-25 18:59:28.756] _msg_get:286 alloc_msg_count: 1 caller: req_get conn: CLIENT sd: 15
[2016-06-25 18:59:28.756] _msg_get:286 alloc_msg_count: 2 caller: rsp_get conn: SERVER sd: 12
[2016-06-25 18:59:28.756] core_close_log:307 close CLIENT 15 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 18:59:28.756] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 18:59:43.756] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33046'
[2016-06-25 18:59:43.757] core_close_log:307 close CLIENT 15 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 18:59:43.757] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 18:59:58.756] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33054'
[2016-06-25 18:59:58.756] core_close_log:307 close CLIENT 15 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 18:59:58.756] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 19:00:03.515] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33058'
[2016-06-25 19:00:13.756] proxy_accept:220 accepted CLIENT 16 on PROXY 13 from '127.0.0.1:33062'
[2016-06-25 19:00:13.756] core_close_log:307 close CLIENT 16 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 19:00:13.756] client_unref_internal_try_put:124 unref conn 0xdf9bb0 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 19:00:18.768] conn_recv_data:614 recv on sd 15 eof rb 77 sb 30
[2016-06-25 19:00:18.768] core_close_log:307 close CLIENT 15 '127.0.0.1:33058' on event 00FF eof 1 done 1 rb 77 sb 30  
[2016-06-25 19:00:18.768] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 19:00:28.756] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33068'
[2016-06-25 19:00:28.756] core_close_log:307 close CLIENT 15 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 19:00:28.756] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
[2016-06-25 19:00:43.756] proxy_accept:220 accepted CLIENT 15 on PROXY 13 from '127.0.0.1:33076'
[2016-06-25 19:00:43.756] core_close_log:307 close CLIENT 15 'unknown' on event FF00FF eof 0 done 0 rb 14 sb 7: Connection reset by peer
[2016-06-25 19:00:43.756] client_unref_internal_try_put:124 unref conn 0xdf5490 owner 0xde90a0 from pool 'dyn_o_mite'
```

We can check Redis log too. With the bash alias rlog.
```bash
[ec2-user@ip-172-31-31-8 dynomite-manager-1]$ rlog 
19899:M 29 Jun 02:27:47.411 * Increased maximum number of open files to 10032 (it was originally set to 1024).
                _._                                                  
           _.-``__ ''-._                                             
      _.-``    `.  `_.  ''-._           Redis 3.0.4 (00000000/0) 64 bit
  .-`` .-```.  ```\/    _.,_ ''-._                                   
 (    '      ,       .-`  | `,    )     Running in standalone mode
 |`-._`-...-` __...-.``-._|'` _.-'|     Port: 22122
 |    `-._   `._    /     _.-'    |     PID: 19899
  `-._    `-._  `-./  _.-'    _.-'                                   
 |`-._`-._    `-.__.-'    _.-'_.-'|                                  
 |    `-._`-._        _.-'_.-'    |           http://redis.io        
  `-._    `-._`-.__.-'_.-'    _.-'                                   
 |`-._`-._    `-.__.-'    _.-'_.-'|                                  
 |    `-._`-._        _.-'_.-'    |                                  
  `-._    `-._`-.__.-'_.-'    _.-'                                   
      `-._    `-.__.-'    _.-'                                       
          `-._        _.-'                                           
              `-.__.-'                                               

19899:M 29 Jun 02:27:47.411 # WARNING: The TCP backlog setting of 511 cannot be enforced because /proc/sys/net/core/somaxconn is set to the lower value of 128.
19899:M 29 Jun 02:27:47.411 # Server started, Redis version 3.0.4
19899:M 29 Jun 02:27:47.411 # WARNING overcommit_memory is set to 0! Background save may fail under low memory condition. To fix this issue add 'vm.overcommit_memory = 1' to /etc/sysctl.conf and then reboot or run the command 'sysctl vm.overcommit_memory=1' for this to take effect.
19899:M 29 Jun 02:27:47.411 # WARNING you have Transparent Huge Pages (THP) support enabled in your kernel. This will create latency and memory usage issues with Redis. To fix this issue run the command 'echo never > /sys/kernel/mm/transparent_hugepage/enabled' as root, and add it to your /etc/rc.local in order to retain the setting after a reboot. Redis must be restarted after THP is disabled.
19899:M 29 Jun 02:27:47.411 * DB loaded from disk: 0.000 seconds
19899:M 29 Jun 02:27:47.411 * The server is now ready to accept connections on port 22122
```

It's time to Download, Build and Install Dynomite Manager
```bash
cd ~
git clone git@github.com:Netflix/dynomite-manager.git
cd dynomite-manager-1/
./gradlew clean build
```

IF works you will see something like this:
```bash
:dynomitemanager:processResources
:dynomitemanager:classes
:dynomitemanager:writeManifestProperties
:dynomitemanager:jar
:dynomitemanager:assemble
:dynomitemanager-web:compileJava UP-TO-DATE
:dynomitemanager-web:processResources UP-TO-DATE
:dynomitemanager-web:classes UP-TO-DATE
:dynomitemanager-web:writeManifestProperties
:dynomitemanager-web:war
Download https://jcenter.bintray.com/xerces/xercesImpl/2.4.0/xercesImpl-2.4.0.pom
Download https://jcenter.bintray.com/xerces/xercesImpl/2.4.0/xercesImpl-2.4.0.jar
:dynomitemanager-web:assemble
:collectNetflixOSS
:dynomitemanager:writeLicenseHeader
:dynomitemanager:licenseMain
Missing header in: dynomitemanager/src/main/resources/log4j.properties
Missing header in: dynomitemanager/src/main/java/com/netflix/dynomitemanager/identity/AwsInstanceEnvIdentity.java
Missing header in: dynomitemanager/src/main/java/com/netflix/dynomitemanager/identity/DefaultVpcInstanceEnvIdentity.java
Missing header in: dynomitemanager/src/main/java/com/netflix/dynomitemanager/identity/LocalInstanceEnvIdentity.java
Missing header in: dynomitemanager/src/main/java/com/netflix/dynomitemanager/identity/InstanceEnvIdentity.java
:dynomitemanager:licenseTest UP-TO-DATE
:dynomitemanager:license
:dynomitemanager:compileTestJava UP-TO-DATE
:dynomitemanager:processTestResources UP-TO-DATE
:dynomitemanager:testClasses UP-TO-DATE
:dynomitemanager:test UP-TO-DATE
:dynomitemanager:check
:dynomitemanager:build
:dynomitemanager-web:writeLicenseHeader
:dynomitemanager-web:licenseMain UP-TO-DATE
:dynomitemanager-web:licenseTest UP-TO-DATE
:dynomitemanager-web:license UP-TO-DATE
:dynomitemanager-web:compileTestJava UP-TO-DATE
:dynomitemanager-web:processTestResources UP-TO-DATE
:dynomitemanager-web:testClasses UP-TO-DATE
:dynomitemanager-web:test UP-TO-DATE
:dynomitemanager-web:check UP-TO-DATE
:dynomitemanager-web:build

BUILD SUCCESSFUL

Total time: 2 mins 7.695 secs

This build could be faster, please consider using the Gradle Daemon: https://docs.gradle.org/2.12/userguide/gradle_daemon.html
[ec2-user@ip-172-31-14-210 dynomite-manager-1]$ 
```

Now we need add the startup script for Dynomite-Manager. Let' create the file first and them add content:
```bash
sudo touch /etc/init.d/dynomite-manager
sudo vim /etc/init.d/dynomite-manager
```

/etc/init.d/dynomite-manager
```bash
#!/bin/bash
# chkconfig: 2345 95 20
# description: This script does some stuff
# processname: java

export JAVA_HOME=/home/ec2-user/jdk1.8.0_45
export JRE_HOME=/home/ec2-user/jdk1.8.0_45/jre
export PATH=$PATH:/home/ec2-user/jdk1.8.0_45/bin:/home/ec2-user/jdk1.8.0_45/jre/bin
export DM_CASSANDRA_CLUSTER_SEEDS="Ip1,ip2,ip3"

export ASG_NAME="asg_dynomite"
export EC2_REGION="us-west-2"
export AUTO_SCALE_GROUP="asg_dynomite"
export NETFLIX_APP="sg_asg_dynomite_florida"

start() {
   echo "Starting Dynomite Manager..."
   cd /home/ec2-user/dynomite-manager-1/
   /home/ec2-user/dynomite-manager-1/gradlew jettyRun > /logs/system/dynomite-manager/dynomite-manager.log & 
}

stop() {
   echo "stoping Dynomite Manager... "
   PID=`ps -ef | grep gradlew | awk '{print $2}' ORS=' ' | awk '{print $1}'`
   if [[ "" !=  "$PID" ]]; then
      echo "killing $PID"
      sudo kill -9 $PID
   fi
}

debug() {
   echo "Starting Dynomite Manager for DEBUG..."
   cd /home/ec2-user/dynomite-manager-1/
   export GRADLE_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n"
   ./gradlew jettyRun &   
}


case "$1" in
"start")
  start
;;
"debug")
  debug
;;
 "stop")
  stop
;;
*)

echo $"Usage: $0 {start|stop|debug}"
RETVAL=1
esac
exit 0

```
You need set your comma separeted Cassandra seeds on DM_CASSANDRA_CLUSTER_SEEDS var.
We also need add permissions and need to enable this script to auto boot up with the box.
```bash
sudo chmod +x /etc/init.d/dynomite-manager
sudo chkconfig dynomite-manager on
```

To make sure it works you can check it.
```bash
sudo chkconfig --list
```

You should see dynomite Manager on the list.
```bash
[ec2-user@ip-172-31-20-132 ~]$ sudo chkconfig --list
acpid          	0:off	1:off	2:on	3:on	4:on	5:on	6:off
atd            	0:off	1:off	2:off	3:on	4:on	5:on	6:off
auditd         	0:off	1:off	2:on	3:on	4:on	5:on	6:off
blk-availability	0:off	1:on	2:on	3:on	4:on	5:on	6:off
cassandra      	0:off	1:off	2:on	3:on	4:on	5:on	6:off
cgconfig       	0:off	1:off	2:off	3:off	4:off	5:off	6:off
cgred          	0:off	1:off	2:off	3:off	4:off	5:off	6:off
cloud-config   	0:off	1:off	2:on	3:on	4:on	5:on	6:off
cloud-final    	0:off	1:off	2:on	3:on	4:on	5:on	6:off
cloud-init     	0:off	1:off	2:on	3:on	4:on	5:on	6:off
cloud-init-local	0:off	1:off	2:on	3:on	4:on	5:on	6:off
crond          	0:off	1:off	2:on	3:on	4:on	5:on	6:off
dynomite       	0:off	1:off	2:on	3:on	4:on	5:on	6:off
dynomite-manager	0:off	1:off	2:on	3:on	4:on	5:on	6:off
ip6tables      	0:off	1:off	2:on	3:on	4:on	5:on	6:off
iptables       	0:off	1:off	2:on	3:on	4:on	5:on	6:off
irqbalance     	0:off	1:off	2:on	3:on	4:on	5:on	6:off
lvm2-monitor   	0:off	1:on	2:on	3:on	4:on	5:on	6:off
mdmonitor      	0:off	1:off	2:on	3:on	4:on	5:on	6:off
messagebus     	0:off	1:off	2:on	3:on	4:on	5:on	6:off
netconsole     	0:off	1:off	2:off	3:off	4:off	5:off	6:off
netfs          	0:off	1:off	2:off	3:on	4:on	5:on	6:off
network        	0:off	1:off	2:on	3:on	4:on	5:on	6:off
nfs            	0:off	1:off	2:off	3:off	4:off	5:off	6:off
nfslock        	0:off	1:off	2:off	3:on	4:on	5:on	6:off
ntpd           	0:off	1:off	2:on	3:on	4:on	5:on	6:off
ntpdate        	0:off	1:off	2:on	3:on	4:on	5:on	6:off
psacct         	0:off	1:off	2:off	3:off	4:off	5:off	6:off
quota_nld      	0:off	1:off	2:off	3:off	4:off	5:off	6:off
rdisc          	0:off	1:off	2:off	3:off	4:off	5:off	6:off
redis_22122    	0:off	1:off	2:on	3:on	4:on	5:on	6:off
redis_6379     	0:off	1:off	2:off	3:off	4:off	5:off	6:off
rngd           	0:off	1:off	2:on	3:on	4:on	5:on	6:off
rpcbind        	0:off	1:off	2:on	3:on	4:on	5:on	6:off
rpcgssd        	0:off	1:off	2:off	3:on	4:on	5:on	6:off
rpcsvcgssd     	0:off	1:off	2:off	3:off	4:off	5:off	6:off
rsyslog        	0:off	1:off	2:on	3:on	4:on	5:on	6:off
saslauthd      	0:off	1:off	2:off	3:off	4:off	5:off	6:off
sendmail       	0:off	1:off	2:on	3:on	4:on	5:on	6:off
sshd           	0:off	1:off	2:on	3:on	4:on	5:on	6:off
udev-post      	0:off	1:on	2:on	3:on	4:on	5:on	6:off
[ec2-user@ip-172-31-20-132 ~]$ 
```

Now we need Download and Install Cassandra. We also will need to setup the schemas with CQL and add some start up script as well. <BR>
BEWARE this is a simpe imstalation in order to you TRY OUT Dynomite manager. This is a single cassandra. You should install a cassandra CLUSTER in your PROD env. <BR>

Download and Install Cassandra
```
cd ~
wget https://archive.apache.org/dist/cassandra/2.1.9/apache-cassandra-2.1.9-bin.tar.gz
tar -xzvf apache-cassandra-2.1.9-bin.tar.gz
rm -rf apache-cassandra-2.1.9-bin.tar.gz
```

Create the Dynomite Manager Schemas. First we need start cassandra.
```
/home/ec2-user/apache-cassandra-2.1.9/
bin/cassandra & 
```

Them we needto creathe de CQL file.
```
touch dynomite-manager.cql
vim dynomite-manager.cql
```

With this content: 
```

CREATE KEYSPACE dyno_bootstrap WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '3'}  AND durable_writes = true;

CREATE TABLE dyno_bootstrap.tokens (
    key text PRIMARY KEY,
    "Id" text,
    "appId" text,
    "availabilityZone" text,
    datacenter text,
    "elasticIP" text,
    hostname text,
    "instanceId" text,
    location text,
    "token" text,
    updatetime timeuuid
) WITH COMPACT STORAGE
    AND bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.SnappyCompressor'}
    AND dclocal_read_repair_chance = 0.0
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 256
    AND read_repair_chance = 1.0
    AND speculative_retry = 'NONE';
CREATE INDEX tokens_appid_idx ON dyno_bootstrap.tokens ("appId");

CREATE TABLE dyno_bootstrap.locks (
    key blob,
    column1 text,
    value blob,
    PRIMARY KEY (key, column1)
) WITH COMPACT STORAGE
    AND CLUSTERING ORDER BY (column1 ASC)
    AND bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.SnappyCompressor'}
    AND dclocal_read_repair_chance = 0.0
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 256
    AND read_repair_chance = 1.0
    AND speculative_retry = 'NONE';

```

And them Import on CQL
```bash
[ec2-user@ip-172-31-14-210 apache-cassandra-2.1.9]$ bin/cqlsh
Connected to Test Cluster at 127.0.0.1:9042.
[cqlsh 5.0.1 | Cassandra 2.1.14 | CQL spec 3.2.1 | Native protocol v3]
Use HELP for help.
cqlsh> SOURCE 'dynomite-manager.cql'
cqlsh> 
```

OK. Now we need to create a startup script for Cassandra.
```bash
sudo touch /etc/init.d/cassandra
sudo vim /etc/init.d/cassandra
```

With this content:
```bash
#!/bin/bash
# chkconfig: 2345 95 20
# description: This script does some stuff
# processname: java

start() {
   echo "Starting cassandra..."
   export JAVA_HOME=/home/ec2-user/jdk1.8.0_45
   export JRE_HOME=/home/ec2-user/jdk1.8.0_45/jre
   export PATH=$PATH:/home/ec2-user/jdk1.8.0_45/bin:/home/ec2-user/jdk1.8.0_45/jre/bin

   cd /home/ec2-user/apache-cassandra-2.1.9
   bin/cassandra start & 
}

stop() {
   echo "stop"
   PID=`ps aux | grep cassandra | grep -v grep | awk '{print $2}'`
   if [[ "" !=  "$PID" ]]; then
      echo "killing $PID"
      sudo kill -9 $PID
   fi
}

case "$1" in start)
  start
;;
  stop)
  stop
;;
*)

echo $"Usage: $0 {start|stop}"
RETVAL=1
esac
exit 0

```

We need add permissions to execute and add to the boot up of the box.
```bash
sudo chmod +x /etc/init.d/cassandra
sudo chkconfig cassandra on
```

Now we need move some files around and create some dirs. I'm doing this to make dynomite work with default settings. <BR>
If you want you can change the configs to point to other folders. <BR>
```bash
sudo mkdir -p /logs/system/dynomite-manager/
sudo mkdir -p /apps/dynomite/conf/
sudo mkdir -p /apps/dynomite/bin/
sudo mkdir -p /mnt/data/nfredis/
sudo mkdir -p /logs/system/
sudo mkdir -p /apps/nfredis/bin/
sudo mkdir -p /logs/system/dynomite/

sudo cp /home/ec2-user/dynomite/bin/kill_dynomite.sh /apps/dynomite/bin/
sudo cp /home/ec2-user/dynomite/bin/launch_dynomite.sh /apps/dynomite/bin/
sudo cp ~/dynomite/src/dynomite /apps/dynomite/bin/
sudo cp ~/dynomite/src/*.* /apps/dynomite/bin/
sudo cp ~/dynomite/conf/dynomite.pem /apps/dynomite/conf/dynomite.pem
sudo cp ~/dynomite/conf/recon_key.pem /apps/dynomite/conf/
sudo cp ~/dynomite/conf/recon_iv.pem /apps/dynomite/conf/

#sudo cp /home/ec2-user/dynomite/bin/core_affinity.sh /apps/dynomite/bin/core_affinity.sh
sudo cp /home/ec2-user/dynomite-manager-1/scripts/core_affinity-centos.sh /apps/dynomite/bin/core_affinity.sh
sudo chmod +x /apps/dynomite/bin/core_affinity.sh

sudo touch /apps/nfredis/bin/launch_nfredis.sh
sudo vim /apps/nfredis/bin/launch_nfredis.sh
#!/bin/bash
sudo service redis_22122 start

sudo chmod +x /apps/nfredis/bin/launch_nfredis.sh
sudo touch /apps/nfredis/bin/kill_redis.sh

sudo vim /apps/nfredis/bin/kill_redis.sh
#!/bin/bash
sudo service redis_22122 stop

sudo chmod +x /apps/nfredis/bin/kill_redis.sh

sudo mkdir -p /mnt/data/nfredis/

sudo chmod -R 777 /logs/
sudo chmod -R 777 /apps/
sudo chmod -R 777 /mnt/

```

All Set. Now let's go to the AWS Ec2 console(https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#Instances:sort=desc:statusChecks) Right button of the mouse on the top of your instance and them Image menu -> Create Image. <BR><BR>

Image Name: BASE_DYNOMITE_MANAGER <BR>
Image Description: BASE_DYNOMITE_MANAGER <BR>
HD: 20GB(Or anything you like it).


## Create Launch Config

Go to the Launch Configuration(https://us-west-2.console.aws.amazon.com/ec2/autoscaling/home?region=us-west-2#LaunchConfigurations:) and create a LC: <BR>

Select My AMIs and them: BASE_DYNOMITE_MANAGER <BR>
Select the Instance Type: m4.large(Or anything you like it). <BR>
As Launch Configuration name: lc_dynomite_manager <BR>
IAM role: dynomite <BR>
Monitoring: Mark - Enable CloudWatch detailed monitoring <BR>
Storage: 20GB(Or anything you like it) <BR>
Security Group: Select - sg_asg_dynomite_florida <BR>
For PEM file pick anyone you want it.

## Create ASG

Now is the time to create a Autoscaling group. Goto ASG(https://us-west-2.console.aws.amazon.com/ec2/autoscaling/home?region=us-west-2#AutoScalingGroups:view=details) and create a new one: <BR>
Select the Launch Configuration: lc_dynomite_manager<BR>
Group name: asg_dynomite <BR>
Group size: 3<BR>
VPC: Use the Default(Or anyone you like it)<BR>
For TAGS: key: name value: asg_dynomite<BR>

All Set! Now we can ssh in one of the 3 boxes to play with the REST operations!


## REST endpoints
```bash
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/takesnapshot
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/start
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/stop
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/cluster_describe
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/startstorageprocess
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/stopstorageprocess
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/backup
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/restore
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/get_seeds
curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/status
```

More details here: https://github.com/Netflix/dynomite-manager/wiki/REST-API

We also can take a look at the dynomite-manager log using the bash alias dmlog.
```bash
[ec2-user@ip-172-31-20-132 bin]$ which dmlog
alias dmlog='tail -f -n 2000 /logs/system/dynomite-manager/dynomite-manager.log'
	/usr/bin/tail
[ec2-user@ip-172-31-20-132 bin]$ sudo rm -rf /logs/system/dynomite-manager/dynomite-manager.log
[ec2-user@ip-172-31-20-132 bin]$ 
[ec2-user@ip-172-31-20-132 bin]$ 
[ec2-user@ip-172-31-20-132 bin]$ cls

[ec2-user@ip-172-31-20-132 bin]$ sudo /etc/init.d/dynomite-manager start
Starting Dynomite Manager...
[ec2-user@ip-172-31-20-132 bin]$ dmlog 
Inferred project: dynomite-manager, version: 0.1.0-SNAPSHOT
The testJar task is deprecated.  Please place common test harness code in its own project and publish separately.
Publication nebula not found in project :.
[buildinfo] Not using buildInfo properties file for this build.
Publication named 'nebula' does not exist for project ':' in task ':artifactoryPublish'.
:dynomitemanager:compileJavawarning: [options] bootstrap class path not set in conjunction with -source 1.7
1 warning

:dynomitemanager:processResources UP-TO-DATE
:dynomitemanager:classes
:dynomitemanager:writeManifestProperties
:dynomitemanager:jar
:dynomitemanager-web:compileJava UP-TO-DATE
:dynomitemanager-web:processResources UP-TO-DATE
:dynomitemanager-web:classes UP-TO-DATE
:dynomitemanager-web:jettyRunSLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/root/.gradle/wrapper/dists/gradle-2.12-bin/avhnk0p45wmm16bas931at19r/gradle-2.12/lib/gradle-core-2.12.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [jar:file:/root/.gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-log4j12/1.7.2/7539c264413b9b1ff9841cd00058c974b7cd1ec9/slf4j-log4j12-1.7.2.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: See http://www.slf4j.org/codes.html#multiple_bindings for an explanation.
SLF4J: Actual binding is of type [org.slf4j.impl.Log4jLoggerFactory]

2016-06-30 03:01:35 INFO  InjectedWebListener:112 - **Binding OSS Config classes.
2016-06-30 03:01:36 WARN  URLConfigurationSource:120 - No URLs will be polled as dynamic configuration sources.
2016-06-30 03:01:36 INFO  URLConfigurationSource:121 - To enable URLs as dynamic configuration sources, define System property archaius.configurationSource.additionalUrls or make config.properties available on classpath.
2016-06-30 03:01:36 INFO  DynamicPropertyFactory:281 - DynamicPropertyFactory is initialized with configuration sources: com.netflix.config.ConcurrentCompositeConfiguration@39785b39
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/placement/availability-zone returns: us-west-2a
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/public-hostname returns: ec2-52-41-84-223.us-west-2.compute.amazonaws.com
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/public-ipv4 returns: 52.41.84.223
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/instance-id returns: i-e084504f
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/instance-type returns: m4.large
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/network/interfaces/macs/ returns: 02:af:8a:49:12:49/
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/network/interfaces/macs/ returns: 02:af:8a:49:12:49/
2016-06-30 03:01:36 INFO  SystemUtils:62 - Calling URL API: http://169.254.169.254/latest/meta-data/network/interfaces/macs/02:af:8a:49:12:49/vpc-id returns: vpc-aeeb50cb
2016-06-30 03:01:36 INFO  DynomitemanagerConfiguration:245 - vpc id for running instance: vpc-aeeb50cb
2016-06-30 03:01:36 INFO  DynomitemanagerConfiguration:278 - Setting up Environmental Variables
2016-06-30 03:01:36 INFO  DynomitemanagerConfiguration:286 - REGION set to us-west-2, ASG Name set to asg_dynomite1
2016-06-30 03:01:36 INFO  PropertiesConfigSource:83 - No Dynomitemanager.properties. Ignore!
2016-06-30 03:01:36 INFO  PropertiesConfigSource:83 - No Dynomitemanager.properties. Ignore!
2016-06-30 03:01:37 INFO  SimpleThreadPool:267 - Job execution threads will use class loader of thread: main
2016-06-30 03:01:37 INFO  SchedulerSignalerImpl:60 - Initialized Scheduler Signaller of type: class org.quartz.core.SchedulerSignalerImpl
2016-06-30 03:01:37 INFO  QuartzScheduler:220 - Quartz Scheduler v.1.7.3 created.
2016-06-30 03:01:37 INFO  RAMJobStore:139 - RAMJobStore initialized.
2016-06-30 03:01:37 INFO  StdSchedulerFactory:1240 - Quartz scheduler 'DefaultQuartzScheduler' initialized from default resource file in Quartz package: 'quartz.properties'
2016-06-30 03:01:37 INFO  StdSchedulerFactory:1244 - Quartz scheduler version: 1.7.3
2016-06-30 03:01:37 INFO  QuartzScheduler:2075 - JobFactory set to: com.netflix.dynomitemanager.sidecore.scheduler.GuiceJobFactory@3875b3c9
2016-06-30 03:01:37 INFO  InstanceDataDAOCassandra:351 - BOOT_CLUSTER = cass_dyno, KS_NAME = dyno_bootstrap
2016-06-30 03:01:37 INFO  CountingConnectionPoolMonitor:194 - AddHost: 127.0.0.1
2016-06-30 03:01:37 INFO  ConnectionPoolMBeanManager:53 - Registering mbean: com.netflix.MonitoredResources:type=ASTYANAX,name=MyConnectionPool,ServiceType=connectionpool
2016-06-30 03:01:38 INFO  UpdateChecker:86 - New update(s) found: 1.8.5 [http://www.terracotta.org/kit/reflector?kitID=default&pageID=QuartzChangeLog]
2016-06-30 03:01:38 INFO  InstanceIdentity:136 - My token: 3530913378
2016-06-30 03:01:38 INFO  FloridaServer:79 - Initializing Florida Server now ...
2016-06-30 03:01:38 INFO  AWSMembership:265 - Fetch current permissions for vpc env of running instance
2016-06-30 03:01:38 INFO  FloridaServer:103 - Running TuneTask and updating configuration.
2016-06-30 03:01:38 INFO  FloridaStandardTuner:136 - dyn_o_mite:
  dyn_listen: 0.0.0.0:8101
  data_store: 0
  listen: 0.0.0.0:8102
  dyn_seed_provider: florida_provider
  servers:
  - 127.0.0.1:22122:1
  tokens: '3530913378'
  auto_eject_hosts: true
  rack: asg_dynomite1
  distribution: vnode
  gos_interval: 10000
  hash: murmur
  preconnect: true
  server_retry_timeout: 30000
  timeout: 5000
  secure_server_option: datacenter
  datacenter: us-west-2
  read_consistency: DC_ONE
  write_consistency: DC_ONE
  pem_key_file: /apps/dynomite/conf/dynomite.pem

2016-06-30 03:01:38 INFO  FloridaStandardTuner:250 - totalMem:8178632 Setting Redis storage max mem to 6081480
2016-06-30 03:01:38 INFO  FloridaStandardTuner:164 - Updating Redis conf: /apps/nfredis/conf/redis.conf
2016-06-30 03:01:38 INFO  FloridaServer:112 - Restore is disabled.
2016-06-30 03:01:38 INFO  FloridaServer:121 - Cold bootstraping, launching dynomite and storage process.
2016-06-30 03:01:38 INFO  FloridaProcessManager:74 - Starting dynomite server joinRing:true
2016-06-30 03:01:43 ERROR FloridaProcessManager:99 - Unable to start Dynomite server. Error code: 1
2016-06-30 03:01:43 INFO  FloridaProcessManager:124 - std_out: MBUF_SIZE=16384
ALLOC_MSGS=200000

dynomite pid: 
taskset: invalid PID argument: '2,5,6'
redis pid: 8124
taskset: failed to set pid 8124's affinity: Invalid argument
pid 8124's current affinity list: 0,1

2016-06-30 03:01:43 INFO  FloridaProcessManager:125 - std_err: 
2016-06-30 03:01:44 INFO  RedisStorageProxy:351 - Checking if Redis needs to be resetted to master
2016-06-30 03:01:44 INFO  ProxyAndStorageResetTask:74 - Checking Dynomite's status
2016-06-30 03:01:44 INFO  ProxyAndStorageResetTask:94 - Dynomite is up and running
2016-06-30 03:01:44 INFO  FloridaServer:142 - Starting task scheduler
2016-06-30 03:01:44 INFO  QuartzScheduler:472 - Scheduler DefaultQuartzScheduler_$_NON_CLUSTERED started.
2016-06-30 03:01:44 INFO  ProcessMonitorTask:165 - Running checkProxyProcess command: ps -ef | grep  '[/]apps/dynomite/bin/dynomite'
2016-06-30 03:01:44 INFO  ProcessMonitorTask:102 - ProcessMonitor state: InstanceState{isSideCarProcessAlive=true, isBootstrapping=false, isStorageProxyAlive=true, isStorageProxyProcessAlive=false, isStorageAlive=true, isHealthy=true, isProcessMonitoringSuspended=false}, time elapsed to check (micros): 40004
2016-06-30 03:01:44 INFO  AWSMembership:265 - Fetch current permissions for vpc env of running instance

```

## Troubleshooting:

## Make sure you have all this OS_ENV vars:
```bash
export ASG_NAME="asg_dynomite"
export AUTO_SCALE_GROUP="asg_dynomite"
export EC2_REGION="us-west-2"
export NETFLIX_APP="sg_asg_dynomite_florida"
```

#### get_seeds

curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/get_seeds

```bash
016-06-23 23:44:32 INFO  ProcessMonitorTask:165 - Running checkProxyProcess command: ps -ef | grep  '[/]apps/dynomite/bin/dynomite'
2016-06-23 23:44:32 INFO  ProcessMonitorTask:102 - ProcessMonitor state: InstanceState{isSideCarProcessAlive=true, isBootstrapping=false, isStorageProxyAlive=true, isStorageProxyProcessAlive=true, isStorageAlive=true, isHealthy=true, isProcessMonitoringSuspended=false}, time elapsted to check (micros): 12779
2016-06-23 23:44:43 ERROR DynomiteAdmin:194 - Cannot find the Seeds
2016-06-23 23:44:47 INFO  ProcessMonitorTask:165 - Running checkProxyProcess command: ps -ef | grep  '[/]apps/dynomite/bin/dynomite'
2016-06-23 23:44:47 INFO  ProcessMonitorTask:102 - ProcessMonitor state: InstanceState{isSideCarProcessAlive=true, isBootstrapping=false, isStorageProxyAlive=true, isStorageProxyProcessAlive=true, isStorageAlive=true, isHealthy=true, isProcessMonitoringSuspended=false}, time elapsted to check (micros): 7216
```

1. Make sure the ASG ash 3 instances
2. Make sure you have the right dynomite.yml config in place

#### s3_backup

curl -v http://localhost:8080/dynomitemanager-web/REST/v1/admin/s3restore

ON S3: 1383429731
Restore: 1286668800000
```bash
2016-06-24 02:26:56 INFO  FloridaProcessManager:168 - Dynomite server has been stopped
2016-06-24 02:26:56 INFO  StorageProcessManager:126 - Stopping Storage process ....
2016-06-24 02:26:59 WARN  JedisUtils:112 - All retries to connect to host:127.0.0.1 port:8102 failed.
2016-06-24 02:26:59 INFO  JedisUtils:54 - Unable to connect
2016-06-24 02:26:59 ERROR BoundedExponentialRetryCallable:64 - Retry #1 for: Failed Jedis connect host:127.0.0.1 port:22122 failed.
2016-06-24 02:27:01 INFO  StorageProcessManager:148 - Storage process has been stopped
2016-06-24 02:27:01 INFO  RestoreFromS3Task:190 - Date to restore to: 20101010
2016-06-24 02:27:01 INFO  RestoreFromS3Task:125 - Restoring data from S3.
2016-06-24 02:27:01 INFO  RestoreFromS3Task:137 - S3 Bucket Name: dynomite-backup
2016-06-24 02:27:01 INFO  RestoreFromS3Task:138 - Key in Bucket: backup/us-west-2/asg_dynomite/1383429731/1286668800000
2016-06-24 02:27:02 ERROR RestoreFromS3Task:166 - AmazonServiceException; request made it to Amazon S3, but was rejected with an error 
2016-06-24 02:27:02 ERROR RestoreFromS3Task:168 - Error Message:    The specified key does not exist. (Service: Amazon S3; Status Code: 404; Error Code: NoSuchKey; Request ID: DA23FBFCA68F27FE)
2016-06-24 02:27:02 ERROR RestoreFromS3Task:169 - HTTP Status Code: 404
2016-06-24 02:27:02 ERROR RestoreFromS3Task:170 - AWS Error Code:   NoSuchKey
2016-06-24 02:27:02 ERROR RestoreFromS3Task:171 - Error Type:       Client
2016-06-24 02:27:02 ERROR RestoreFromS3Task:172 - Request ID:       DA23FBFCA68F27FE
2016-06-24 02:27:02 ERROR RestoreFromS3Task:111 - S3 Restore not successful: Starting storage process without loading data.
```

1. BEWARE of the DATE diff you need have file based on time diff. Check RestoreFromS3Task.java
2. Need have permissions on: /mnt/data/

#### position must be >= 0 

```bash
2016-06-25 01:18:09 ERROR RetryableCallable:72 - Retry #1 for: position must be >= 0
2016-06-25 01:18:09 ERROR RetryableCallable:75 - Exception --> java.lang.IllegalArgumentException: position must be >= 0
	at com.google.common.base.Preconditions.checkArgument(Preconditions.java:122)
	at com.netflix.dynomitemanager.sidecore.utils.TokenManager.initialToken(TokenManager.java:47)
	at com.netflix.dynomitemanager.sidecore.utils.TokenManager.createToken(TokenManager.java:75)
	at com.netflix.dynomitemanager.identity.InstanceIdentity$GetNewToken.retriableCall(InstanceIdentity.java:245)
	at com.netflix.dynomitemanager.identity.InstanceIdentity$GetNewToken.retriableCall(InstanceIdentity.java:220)
	at com.netflix.dynomitemanager.sidecore.utils.RetryableCallable.call(RetryableCallable.java:59)
	at com.netflix.dynomitemanager.identity.InstanceIdentity.init(InstanceIdentity.java:134)
	at com.netflix.dynomitemanager.identity.InstanceIdentity.<init>(InstanceIdentity.java:86)
	at com.netflix.dynomitemanager.identity.InstanceIdentity$$FastClassByGuice$$17e6ff76.newInstance(<generated>)
	at com.google.inject.internal.cglib.reflect.$FastConstructor.newInstance(FastConstructor.java:40)
	at com.google.inject.internal.DefaultConstructionProxyFactory$1.newInstance(DefaultConstructionProxyFactory.java:60)
	at com.google.inject.internal.ConstructorInjector.construct(ConstructorInjector.java:85)
	at com.google.inject.internal.ConstructorBindingImpl$Factory.get(ConstructorBindingImpl.java:254)
	at com.google.inject.internal.ProviderToInternalFactoryAdapter$1.call(ProviderToInternalFactoryAdapter.java:46)
	at com.google.inject.internal.InjectorImpl.callInContext(InjectorImpl.java:1031)
	at com.google.inject.internal.ProviderToInternalFactoryAdapter.get(ProviderToInternalFactoryAdapter.java:40)
	at com.google.inject.Scopes$1$1.get(Scopes.java:65)
	at com.google.inject.internal.InternalFactoryToProviderAdapter.get(InternalFactoryToProviderAdapter.java:40)
	at com.google.inject.internal.SingleParameterInjector.inject(SingleParameterInjector.java:38)
	at com.google.inject.internal.SingleParameterInjector.getAll(SingleParameterInjector.java:62)
	at com.google.inject.internal.ConstructorInjector.construct(ConstructorInjector.java:84)
	at com.google.inject.internal.ConstructorBindingImpl$Factory.get(ConstructorBindingImpl.java:254)
	at com.google.inject.internal.ProviderToInternalFactoryAdapter$1.call(ProviderToInternalFactoryAdapter.java:46)
	at com.google.inject.internal.InjectorImpl.callInContext(InjectorImpl.java:1031)
	at com.google.inject.internal.ProviderToInternalFactoryAdapter.get(ProviderToInternalFactoryAdapter.java:40)
	at com.google.inject.Scopes$1$1.get(Scopes.java:65)
	at com.google.inject.internal.InternalFactoryToProviderAdapter.get(InternalFactoryToProviderAdapter.java:40)
	at com.google.inject.internal.InjectorImpl$4$1.call(InjectorImpl.java:978)
	at com.google.inject.internal.InjectorImpl.callInContext(InjectorImpl.java:1024)
	at com.google.inject.internal.InjectorImpl$4.get(InjectorImpl.java:974)
	at com.google.inject.internal.InjectorImpl.getInstance(InjectorImpl.java:1013)
	at com.netflix.dynomitemanager.defaultimpl.InjectedWebListener.getInjector(InjectedWebListener.java:83)
	at com.google.inject.servlet.GuiceServletContextListener.contextInitialized(GuiceServletContextListener.java:45)
	at org.mortbay.jetty.handler.ContextHandler.startContext(ContextHandler.java:548)
	at org.mortbay.jetty.servlet.Context.startContext(Context.java:136)
	at org.mortbay.jetty.webapp.WebAppContext.startContext(WebAppContext.java:1272)
	at org.mortbay.jetty.handler.ContextHandler.doStart(ContextHandler.java:517)
	at org.mortbay.jetty.webapp.WebAppContext.doStart(WebAppContext.java:489)
	at org.gradle.api.plugins.jetty.internal.JettyPluginWebAppContext.doStart(JettyPluginWebAppContext.java:112)
	at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
	at org.mortbay.jetty.handler.HandlerCollection.doStart(HandlerCollection.java:152)
	at org.mortbay.jetty.handler.ContextHandlerCollection.doStart(ContextHandlerCollection.java:156)
	at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
	at org.mortbay.jetty.handler.HandlerCollection.doStart(HandlerCollection.java:152)
	at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
	at org.mortbay.jetty.handler.HandlerWrapper.doStart(HandlerWrapper.java:130)
	at org.mortbay.jetty.Server.doStart(Server.java:224)
	at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
	at org.gradle.api.plugins.jetty.internal.Jetty6PluginServer.start(Jetty6PluginServer.java:111)
	at org.gradle.api.plugins.jetty.AbstractJettyRunTask.startJettyInternal(AbstractJettyRunTask.java:238)
	at org.gradle.api.plugins.jetty.AbstractJettyRunTask.startJetty(AbstractJettyRunTask.java:191)
	at org.gradle.api.plugins.jetty.AbstractJettyRunTask.start(AbstractJettyRunTask.java:162)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:497)
	at org.gradle.internal.reflect.JavaMethod.invoke(JavaMethod.java:75)
	at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.doExecute(AnnotationProcessingTaskFactory.java:227)
	at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.execute(AnnotationProcessingTaskFactory.java:220)
	at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.execute(AnnotationProcessingTaskFactory.java:209)
	at org.gradle.api.internal.AbstractTask$TaskActionWrapper.execute(AbstractTask.java:585)
	at org.gradle.api.internal.AbstractTask$TaskActionWrapper.execute(AbstractTask.java:568)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeAction(ExecuteActionsTaskExecuter.java:80)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:61)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.PostExecutionAnalysisTaskExecuter.execute(PostExecutionAnalysisTaskExecuter.java:35)
	at org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter.execute(SkipUpToDateTaskExecuter.java:64)
	at org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter.execute(ValidatingTaskExecuter.java:58)
	at org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter.execute(SkipEmptySourceFilesTaskExecuter.java:52)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:52)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:53)
	at org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter.execute(ExecuteAtMostOnceTaskExecuter.java:43)
	at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter$EventFiringTaskWorker.execute(DefaultTaskGraphExecuter.java:203)
	at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter$EventFiringTaskWorker.execute(DefaultTaskGraphExecuter.java:185)
	at org.gradle.execution.taskgraph.AbstractTaskPlanExecutor$TaskExecutorWorker.processTask(AbstractTaskPlanExecutor.java:66)
	at org.gradle.execution.taskgraph.AbstractTaskPlanExecutor$TaskExecutorWorker.run(AbstractTaskPlanExecutor.java:50)
	at org.gradle.execution.taskgraph.DefaultTaskPlanExecutor.process(DefaultTaskPlanExecutor.java:25)
	at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter.execute(DefaultTaskGraphExecuter.java:110)
	at org.gradle.execution.SelectedTaskExecutionAction.execute(SelectedTaskExecutionAction.java:37)
	at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:37)
	at org.gradle.execution.DefaultBuildExecuter.access$000(DefaultBuildExecuter.java:23)
	at org.gradle.execution.DefaultBuildExecuter$1.proceed(DefaultBuildExecuter.java:43)
	at org.gradle.execution.DryRunBuildExecutionAction.execute(DryRunBuildExecutionAction.java:32)
	at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:37)
	at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:30)
	at org.gradle.initialization.DefaultGradleLauncher$4.run(DefaultGradleLauncher.java:154)
	at org.gradle.internal.Factories$1.create(Factories.java:22)
	at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:90)
	at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:52)
	at org.gradle.initialization.DefaultGradleLauncher.doBuildStages(DefaultGradleLauncher.java:151)
	at org.gradle.initialization.DefaultGradleLauncher.access$200(DefaultGradleLauncher.java:32)
	at org.gradle.initialization.DefaultGradleLauncher$1.create(DefaultGradleLauncher.java:99)
	at org.gradle.initialization.DefaultGradleLauncher$1.create(DefaultGradleLauncher.java:93)
	at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:90)
	at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:62)
	at org.gradle.initialization.DefaultGradleLauncher.doBuild(DefaultGradleLauncher.java:93)
	at org.gradle.initialization.DefaultGradleLauncher.run(DefaultGradleLauncher.java:82)
	at org.gradle.launcher.exec.InProcessBuildActionExecuter$DefaultBuildController.run(InProcessBuildActionExecuter.java:94)
	at org.gradle.tooling.internal.provider.ExecuteBuildActionRunner.run(ExecuteBuildActionRunner.java:28)
	at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
	at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:43)
	at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:28)
	at org.gradle.launcher.exec.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:75)
	at org.gradle.launcher.exec.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:45)
	at org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter.execute(DaemonUsageSuggestingBuildActionExecuter.java:51)
	at org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter.execute(DaemonUsageSuggestingBuildActionExecuter.java:28)
	at org.gradle.launcher.cli.RunBuildAction.run(RunBuildAction.java:43)
	at org.gradle.internal.Actions$RunnableActionAdapter.execute(Actions.java:170)
	at org.gradle.launcher.cli.CommandLineActionFactory$ParseAndBuildAction.execute(CommandLineActionFactory.java:237)
	at org.gradle.launcher.cli.CommandLineActionFactory$ParseAndBuildAction.execute(CommandLineActionFactory.java:210)
	at org.gradle.launcher.cli.JavaRuntimeValidationAction.execute(JavaRuntimeValidationAction.java:35)
	at org.gradle.launcher.cli.JavaRuntimeValidationAction.execute(JavaRuntimeValidationAction.java:24)
	at org.gradle.launcher.cli.CommandLineActionFactory$WithLogging.execute(CommandLineActionFactory.java:206)
	at org.gradle.launcher.cli.CommandLineActionFactory$WithLogging.execute(CommandLineActionFactory.java:169)
	at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:33)
	at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:22)
	at org.gradle.launcher.Main.doAction(Main.java:33)
	at org.gradle.launcher.bootstrap.EntryPoint.run(EntryPoint.java:45)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:497)
	at org.gradle.launcher.bootstrap.ProcessBootstrap.runNoExit(ProcessBootstrap.java:54)
	at org.gradle.launcher.bootstrap.ProcessBootstrap.run(ProcessBootstrap.java:35)
	at org.gradle.launcher.GradleMain.main(GradleMain.java:23)
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:497)
	at org.gradle.wrapper.BootstrapMainStarter.start(BootstrapMainStarter.java:30)
	at org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:129)
	at org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:61)

```

This means you are not running Dynomite Manager whithin an ASG(Auto Scalling Group), Once you do it, shoudl fix the problem.

#### Unable to get group-id or group-name 

```bash
2016-06-30 02:36:16 ERROR AWSMembership:191 - unable to get group-id for group-name=asg_dynomite1 vpc-id=vpc-aeeb50cb
2016-06-30 02:36:16 ERROR Task:99 - Couldnt execute the task because of The request must contain the parameter groupName or groupId (Service: AmazonEC2; Status Code: 400; Error Code: MissingParameter; Request ID: b211f8ad-150d-4148-9d14-9b08fcce93a5)
com.amazonaws.AmazonServiceException: The request must contain the parameter groupName or groupId (Service: AmazonEC2; Status Code: 400; Error Code: MissingParameter; Request ID: b211f8ad-150d-4148-9d14-9b08fcce93a5)
    at com.amazonaws.http.AmazonHttpClient.handleErrorResponse(AmazonHttpClient.java:1383)
    at com.amazonaws.http.AmazonHttpClient.executeOneRequest(AmazonHttpClient.java:902)
    at com.amazonaws.http.AmazonHttpClient.executeHelper(AmazonHttpClient.java:607)
    at com.amazonaws.http.AmazonHttpClient.doExecute(AmazonHttpClient.java:376)
    at com.amazonaws.http.AmazonHttpClient.executeWithTimer(AmazonHttpClient.java:338)
    at com.amazonaws.http.AmazonHttpClient.execute(AmazonHttpClient.java:287)
    at com.amazonaws.services.ec2.AmazonEC2Client.invoke(AmazonEC2Client.java:11128)
    at com.amazonaws.services.ec2.AmazonEC2Client.authorizeSecurityGroupIngress(AmazonEC2Client.java:1019)
    at com.netflix.dynomitemanager.sidecore.aws.AWSMembership.addACL(AWSMembership.java:153)
    at com.netflix.dynomitemanager.sidecore.aws.UpdateSecuritySettings.execute(UpdateSecuritySettings.java:70)
    at com.netflix.dynomitemanager.sidecore.scheduler.Task.execute(Task.java:93)
    at org.quartz.core.JobRunShell.run(JobRunShell.java:199)
    at org.quartz.simpl.SimpleThreadPool$WorkerThread.run(SimpleThreadPool.java:546)


2016-06-30 02:44:10 INFO  FloridaServer:79 - Initializing Florida Server now ...
2016-06-30 02:44:10 INFO  AWSMembership:269 - Fetch current permissions for vpc env of running instance
2016-06-30 02:44:10 ERROR AWSMembership:191 - unable to get group-id for group-name=asg_dynomite vpc-id=vpc-aeeb50cb
2016-06-30 02:44:10 ERROR Task:99 - Couldnt execute the task because of The request must contain the parameter groupName or groupId (Service: AmazonEC2; Status Code: 400; Error Code: MissingParameter; Request ID: 85d82dba-e3b2-4e30-9087-00a84371ed69)
com.amazonaws.AmazonServiceException: The request must contain the parameter groupName or groupId (Service: AmazonEC2; Status Code: 400; Error Code: MissingParameter; Request ID: 85d82dba-e3b2-4e30-9087-00a84371ed69)
    at com.amazonaws.http.AmazonHttpClient.handleErrorResponse(AmazonHttpClient.java:1383)
    at com.amazonaws.http.AmazonHttpClient.executeOneRequest(AmazonHttpClient.java:902)
    at com.amazonaws.http.AmazonHttpClient.executeHelper(AmazonHttpClient.java:607)
    at com.amazonaws.http.AmazonHttpClient.doExecute(AmazonHttpClient.java:376)
    at com.amazonaws.http.AmazonHttpClient.executeWithTimer(AmazonHttpClient.java:338)
    at com.amazonaws.http.AmazonHttpClient.execute(AmazonHttpClient.java:287)
    at com.amazonaws.services.ec2.AmazonEC2Client.invoke(AmazonEC2Client.java:11128)
    at com.amazonaws.services.ec2.AmazonEC2Client.authorizeSecurityGroupIngress(AmazonEC2Client.java:1019)
    at com.netflix.dynomitemanager.sidecore.aws.AWSMembership.addACL(AWSMembership.java:153)
    at com.netflix.dynomitemanager.sidecore.aws.UpdateSecuritySettings.execute(UpdateSecuritySettings.java:70)
    at com.netflix.dynomitemanager.sidecore.scheduler.Task.execute(Task.java:93)
    at com.netflix.dynomitemanager.sidecore.scheduler.TaskScheduler.runTaskNow(TaskScheduler.java:98)
    at com.netflix.dynomitemanager.FloridaServer.initialize(FloridaServer.java:84)
    at com.netflix.dynomitemanager.defaultimpl.InjectedWebListener.getInjector(InjectedWebListener.java:83)
    at com.google.inject.servlet.GuiceServletContextListener.contextInitialized(GuiceServletContextListener.java:45)
    at org.mortbay.jetty.handler.ContextHandler.startContext(ContextHandler.java:548)
    at org.mortbay.jetty.servlet.Context.startContext(Context.java:136)
    at org.mortbay.jetty.webapp.WebAppContext.startContext(WebAppContext.java:1272)
    at org.mortbay.jetty.handler.ContextHandler.doStart(ContextHandler.java:517)
    at org.mortbay.jetty.webapp.WebAppContext.doStart(WebAppContext.java:489)
    at org.gradle.api.plugins.jetty.internal.JettyPluginWebAppContext.doStart(JettyPluginWebAppContext.java:112)
    at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
    at org.mortbay.jetty.handler.HandlerCollection.doStart(HandlerCollection.java:152)
    at org.mortbay.jetty.handler.ContextHandlerCollection.doStart(ContextHandlerCollection.java:156)
    at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
    at org.mortbay.jetty.handler.HandlerCollection.doStart(HandlerCollection.java:152)
    at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
    at org.mortbay.jetty.handler.HandlerWrapper.doStart(HandlerWrapper.java:130)
    at org.mortbay.jetty.Server.doStart(Server.java:224)
    at org.mortbay.component.AbstractLifeCycle.start(AbstractLifeCycle.java:50)
    at org.gradle.api.plugins.jetty.internal.Jetty6PluginServer.start(Jetty6PluginServer.java:111)
    at org.gradle.api.plugins.jetty.AbstractJettyRunTask.startJettyInternal(AbstractJettyRunTask.java:238)
    at org.gradle.api.plugins.jetty.AbstractJettyRunTask.startJetty(AbstractJettyRunTask.java:191)
    at org.gradle.api.plugins.jetty.AbstractJettyRunTask.start(AbstractJettyRunTask.java:162)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke(Method.java:497)
    at org.gradle.internal.reflect.JavaMethod.invoke(JavaMethod.java:75)
    at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.doExecute(AnnotationProcessingTaskFactory.java:227)
    at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.execute(AnnotationProcessingTaskFactory.java:220)
    at org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory$StandardTaskAction.execute(AnnotationProcessingTaskFactory.java:209)
    at org.gradle.api.internal.AbstractTask$TaskActionWrapper.execute(AbstractTask.java:585)
    at org.gradle.api.internal.AbstractTask$TaskActionWrapper.execute(AbstractTask.java:568)
    at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeAction(ExecuteActionsTaskExecuter.java:80)
    at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeActions(ExecuteActionsTaskExecuter.java:61)
    at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:46)
    at org.gradle.api.internal.tasks.execution.PostExecutionAnalysisTaskExecuter.execute(PostExecutionAnalysisTaskExecuter.java:35)
    at org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter.execute(SkipUpToDateTaskExecuter.java:64)
    at org.gradle.api.internal.tasks.execution.ValidatingTaskExecuter.execute(ValidatingTaskExecuter.java:58)
    at org.gradle.api.internal.tasks.execution.SkipEmptySourceFilesTaskExecuter.execute(SkipEmptySourceFilesTaskExecuter.java:52)
    at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:52)
    at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:53)
    at org.gradle.api.internal.tasks.execution.ExecuteAtMostOnceTaskExecuter.execute(ExecuteAtMostOnceTaskExecuter.java:43)
    at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter$EventFiringTaskWorker.execute(DefaultTaskGraphExecuter.java:203)
    at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter$EventFiringTaskWorker.execute(DefaultTaskGraphExecuter.java:185)
    at org.gradle.execution.taskgraph.AbstractTaskPlanExecutor$TaskExecutorWorker.processTask(AbstractTaskPlanExecutor.java:66)
    at org.gradle.execution.taskgraph.AbstractTaskPlanExecutor$TaskExecutorWorker.run(AbstractTaskPlanExecutor.java:50)
    at org.gradle.execution.taskgraph.DefaultTaskPlanExecutor.process(DefaultTaskPlanExecutor.java:25)
    at org.gradle.execution.taskgraph.DefaultTaskGraphExecuter.execute(DefaultTaskGraphExecuter.java:110)
    at org.gradle.execution.SelectedTaskExecutionAction.execute(SelectedTaskExecutionAction.java:37)
    at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:37)
    at org.gradle.execution.DefaultBuildExecuter.access$000(DefaultBuildExecuter.java:23)
    at org.gradle.execution.DefaultBuildExecuter$1.proceed(DefaultBuildExecuter.java:43)
    at org.gradle.execution.DryRunBuildExecutionAction.execute(DryRunBuildExecutionAction.java:32)
    at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:37)
    at org.gradle.execution.DefaultBuildExecuter.execute(DefaultBuildExecuter.java:30)
    at org.gradle.initialization.DefaultGradleLauncher$4.run(DefaultGradleLauncher.java:154)
    at org.gradle.internal.Factories$1.create(Factories.java:22)
    at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:90)
    at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:52)
    at org.gradle.initialization.DefaultGradleLauncher.doBuildStages(DefaultGradleLauncher.java:151)
    at org.gradle.initialization.DefaultGradleLauncher.access$200(DefaultGradleLauncher.java:32)
    at org.gradle.initialization.DefaultGradleLauncher$1.create(DefaultGradleLauncher.java:99)
    at org.gradle.initialization.DefaultGradleLauncher$1.create(DefaultGradleLauncher.java:93)
    at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:90)
    at org.gradle.internal.progress.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:62)
    at org.gradle.initialization.DefaultGradleLauncher.doBuild(DefaultGradleLauncher.java:93)
    at org.gradle.initialization.DefaultGradleLauncher.run(DefaultGradleLauncher.java:82)
    at org.gradle.launcher.exec.InProcessBuildActionExecuter$DefaultBuildController.run(InProcessBuildActionExecuter.java:94)
    at org.gradle.tooling.internal.provider.ExecuteBuildActionRunner.run(ExecuteBuildActionRunner.java:28)
    at org.gradle.launcher.exec.ChainingBuildActionRunner.run(ChainingBuildActionRunner.java:35)
    at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:43)
    at org.gradle.launcher.exec.InProcessBuildActionExecuter.execute(InProcessBuildActionExecuter.java:28)
    at org.gradle.launcher.exec.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:75)
    at org.gradle.launcher.exec.ContinuousBuildActionExecuter.execute(ContinuousBuildActionExecuter.java:45)
    at org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter.execute(DaemonUsageSuggestingBuildActionExecuter.java:51)
    at org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter.execute(DaemonUsageSuggestingBuildActionExecuter.java:28)
    at org.gradle.launcher.cli.RunBuildAction.run(RunBuildAction.java:43)
    at org.gradle.internal.Actions$RunnableActionAdapter.execute(Actions.java:170)
    at org.gradle.launcher.cli.CommandLineActionFactory$ParseAndBuildAction.execute(CommandLineActionFactory.java:237)
    at org.gradle.launcher.cli.CommandLineActionFactory$ParseAndBuildAction.execute(CommandLineActionFactory.java:210)
    at org.gradle.launcher.cli.JavaRuntimeValidationAction.execute(JavaRuntimeValidationAction.java:35)
    at org.gradle.launcher.cli.JavaRuntimeValidationAction.execute(JavaRuntimeValidationAction.java:24)
    at org.gradle.launcher.cli.CommandLineActionFactory$WithLogging.execute(CommandLineActionFactory.java:206)
    at org.gradle.launcher.cli.CommandLineActionFactory$WithLogging.execute(CommandLineActionFactory.java:169)
    at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:33)
    at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:22)
    at org.gradle.launcher.Main.doAction(Main.java:33)
    at org.gradle.launcher.bootstrap.EntryPoint.run(EntryPoint.java:45)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke(Method.java:497)
    at org.gradle.launcher.bootstrap.ProcessBootstrap.runNoExit(ProcessBootstrap.java:54)
    at org.gradle.launcher.bootstrap.ProcessBootstrap.run(ProcessBootstrap.java:35)
    at org.gradle.launcher.GradleMain.main(GradleMain.java:23)
    at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
    at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
    at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
    at java.lang.reflect.Method.invoke(Method.java:497)
    at org.gradle.wrapper.BootstrapMainStarter.start(BootstrapMainStarter.java:30)
    at org.gradle.wrapper.WrapperExecutor.execute(WrapperExecutor.java:129)
    at org.gradle.wrapper.GradleWrapperMain.main(GradleWrapperMain.java:61)
```

Make sure you have the OS env var called NETFLIX_APP pointing to the right Security Group name. 

## More on Redis Persistence check
```bash
Look for the BGREWRITEAOF Command
http://redis.io/topics/persistence
```

## Some important Classes for Configs:

```java
InjectedWebListener
DynomitemanagerConfiguration
DynomiteAdmin
FloridaProcessManager
RedisStorageProxy
SnapshotBackup
InstanceProfileCredentialsProvider
EC2MetadataClient
```