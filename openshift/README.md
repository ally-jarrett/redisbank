## Install and Configure Java

> Note: this is an optional step, you can use any Java version starting from 11 and above

Install Java 17 on your local machine:
```
❯ tar xvf OpenJDK17U-jdk_x64_linux_hotspot_17.0.2_8.tar.gz

❯ sudo mv jdk-17.0.2+8 /opt/java/
```

Add Java to the profile:
```
❯ nano .profile
```

Modify the file:
```
# adding java to the path
PATH=$PATH:/opt/java/jdk-17.0.2+8/bin:/opt/java/jdk-17.0.2+8/lib
JAVA_HOME=/opt/java/jdk-17.0.2+8
```

Reload the profile and check java version
```
❯ source .profile
❯ java -version
```

Configure the VSCode (add to your settings.json):

```
    "java.configuration.runtimes": [
        {
          "name": "JavaSE-17",
          "path": "/opt/java/jdk-17.0.2+8",
          "default": true
        },
      ]
```

## Install Redis Operator and Create Redis Cluster

> Note: you should already have your OpenShift cluster installed and configured at this point. Check [RedHat documentation](https://docs.openshift.com/container-platform/4.9/installing/index.html) for details.

> Note: you also need to have OpenShift CLI installed on your local machine. Check [RedHat documentation](https://docs.openshift.com/container-platform/4.9/cli_reference/openshift_cli/getting-started-cli.html) on how to get it installed.

Export KUBECONFIG to point to your OpenShift cluster config:
```
export KUBECONFIG=/<your-cluster-configuration-dir>/auth/kubeconfig
```

Create the new project (namespace) for Redis cluster:
```
oc new-project redis-ent-dev
```
Follow [this documentation](https://github.com/RedisLabs/redis-enterprise-k8s-docs/tree/master/openshift/OLM) to find and install _Redis Enterprise Operator_ in the OperatorHub - make sure to install it to the project you created in the prior step:
![](images/install_operator.png)

> Note: this guide was created to simplify the deployment of RedisBank application in OpenShift environment. For production workloads you almost always want to deploy your workloads separately (to a different namespace) from Redis Enterprise namespace. 

## Create Redis Database

First, create a secret for a default user in your database:

```
oc apply -f bankdb-pwd.yaml
```

> Note: if you want to modify the password, make sure you also change it in the `openshift` configuration for the application (application-openshift.properties).

To create a new database you can use the [provided resource file](bankdb.yaml):

> Note: modify the name and namespace for your database accordingly

> Note: the list of modules that the cluster supports can be found at the very bottom of your REC cluster YAML file:

![](images/modules_versions.png)

```
oc apply -f bankdb.yaml
```

## Build and Deploy the Application

We are going to use [Eclipse JKube Maven Plugin](https://www.eclipse.org/jkube/docs/openshift-maven-plugin#_configuration) for Kubernetes and OpenShift to automate the resources creation and also implement S2I (source to image) build process that is often used in OpenShift environment.

### Configure

In `pom.xml` add [jkube](https://github.com/eclipse/jkube/tree/master/openshift-maven-plugin) maven plugin - it will help to generate the required resources and deploy the application to OpenShift:

```
<plugin>
	<groupId>org.eclipse.jkube</groupId>
	<artifactId>openshift-maven-plugin</artifactId>
	<version>1.7.0</version>
</plugin>
```

In `src/main/resources/application-openshift.properties` change the following settings:

```
# stomp.host=<app-name-namespace>.apps.<cluster-name>.<dns-zone>
# for example:
stomp.host=redisbank-redis-ent-dev.apps.c1.openshift.demo.redislabs.com
stomp.protocol=ws
stomp.port=80
spring.redis.host=bankdb
spring.redis.port=12869
spring.redis.password=redisbank
```

> Note: make sure your application is using the `openshift` profile. One way to set this in a Spring application is to modify the `application.properties` file:

```
spring.profiles.active=openshift
```

### Build Locally

This will create and repackage the jar file with all the required resources:

```
./mvnw clean package -Dmaven.test.skip=true
```

### Build Remotely

This command will trigger the remote S2I build process. It will also create the _ImageSet_ and the _BuildConfig_ in your OpenShift cluster if not created yet:

```
./mvnw oc:build -Dmaven.test.skip=true 
```

### Deploy

Generate the _DeploymentConfig_ resources for your application:
```
./mvnw oc:resource
```

Apply the resources to create the _DeploymentConfig_, _Service_ and _Route_ generated in a previous step:

```
./mvnw oc:apply
```

> Note: you can use the undeploy command: `./mvnw oc:undeploy` if you need to undepoy the application completely

At this point of time your application is already deployed and supposed to be up and running. Go check the your Openshift web console on the status and pull the route url to access the app from your browser. This will typically look like this:

`http://redisbank-<namespace>.<cluster-name>.<dns-zone>`


## Depyloying with Jenkind &  ArgoCD 

There is some housekeeping first to ensure all our projects talk to each other. 

Firstly we'll use the following namespaces: 
- argocd `$ oc new-project argocd --display-name 'ArgoCD'`
- devops `$ oc new-project devops --display-name 'DevOps'`
- redis-apps `$ oc new-project redis-apps --display-name 'Redis Apps'`

It's a bit verbose, but for now it allows easy separation of tools and gives us a clear separation of concerns, so we can easily see how everything fits together.

- Deploy the ArgoCD Operator + Create a new ArgoCD instance. 

### Deploy a new Jenkins Instance into the DevOps namespace.
We need to deploy a Jenkins image, which will coordinate our build pipelines. 
We'll also need to define the 'BuildConfig' of our project.

**NOTE**: BuildConfig in OCP terms, defines how an image should be built and where that image should be pushed to. In this instance we will be building and pushing the image to the internal Openshift repository. 

1. View the available Jenkins templates 
```bash
   $ oc get templates -n openshift | grep jenkins
   
   jenkins-ephemeral                               Jenkins service, without persistent storage....                                    8 (all set)       6
   jenkins-ephemeral-monitored                     Jenkins service, without persistent storage....                                    9 (all set)       7
   jenkins-persistent                              Jenkins service, with persistent storage....                                       10 (all set)      7
   jenkins-persistent-monitored                    Jenkins service, with persistent storage....
```

2. Deploy the Jenkins Template:  
```bash
    $oc new-app jenkins-ephemeral -e MEMORY_LIMIT=2Gi
    
--> Deploying template "openshift/jenkins-ephemeral" to project bookinfo

     Jenkins (Ephemeral)
     ---------
     Jenkins service, without persistent storage.
...
--> Success
    Access your application via route 'jenkins-bookinfo.apps.cluster-8faf.8faf.sandbox1706.opentlc.com' 
    Run 'oc status' to view your app. 
```

3. View the status of the deployments: 
```bash
$ oc get pods

NAME                             READY   STATUS      RESTARTS   AGE
pod/jenkins-1-deploy             0/1     Completed   0          96s
jenkins-2-shf2w                  1/1     Running     0          22m
```
4. Login via generated URL e.g. `jenkins-devops.apps.uki-okd.demo.redislabs.com`
```bash
$ oc get routes

NAME      HOST/PORT                                        PATH   SERVICES   PORT    TERMINATION     WILDCARD
jenkins   jenkins-devops.apps.uki-okd.demo.redislabs.com          jenkins    <all>   edge/Redirect   None
```
NOTE: You should be able to login using your OKD/Openshift credentials 

5. Create the BuildConfig & ImageStreams. 
```bash
$ oc apply -f openshift/openshift-build.yml 

buildconfig.build.openshift.io/redisbank created
imagestream.image.openshift.io/redisbank created
```

6. Finally we'll be deploying our apps into the 'redis-apps' namespace/project, so we need to enable the redis-apps namespace to have access to the redisbank ImageStream in the 'devops' namespace

```bash
$ oc policy add-role-to-user \
    system:image-puller system:serviceaccount:redis-apps:default \
    -n devops
    
clusterrole.rbac.authorization.k8s.io/system:image-puller added: "system:serviceaccount:redis-apps:default"
```
