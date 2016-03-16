Sample Knox LDAP Realm Enhancement
==================================
This sample project shows a simple extension of the existing KnoxLdapRealm.
This new Shiro Realm implementation supports additional dynamic behavior for user search base.
It also allows for more advanced control of the search filter used within the search base.

The dynamic behavior is based on applying a regular expression to the incoming principal.
The matched groups of the regular expression can then be used as inputs to create the user search base and filter.

The example used here takes the input principal 'Users\tom'
1. Searches for and authenticates 'CN=sam,CN=Users,DC=hwqe,DC=hortonworks,DC=com'
2. Propagates identity 'Users+tom' to the back end

The table below shows the general configuration that will be used to accomplish this.
The configuration of the identity mapping as described in step #2 above isn't specifically covered here.
However you will find it in the topology file below in the identity-assertion/Regex provider section.

| Attribute        | Sample                                      | Comments                       |
|------------------|---------------------------------------------| -------------------------------|
| principalRegex   | (.\*?)\\\\(.\*)                             | Regular expression with groups |
| userSearchBase   | CN={1},DC=hwqe,DC=hortonworks,DC=com        | {1} will be domain name        |
| userSearchFilter | (&(objectclass=person)(sAMAccountName={2})) | {2} will be user name          |

This table shows the results of the configuration above applied to a principal of 'US\kminder'.

| Attribute        | Sample                                      | Comments |
|------------------|---------------------------------------------| ---------|
| principal        | US\\tom                                     |          |
| userSearchBase   | CN=US,DC=hwqe,DC=hortonworks,DC=com         |          |
| userSearchFilter | (&(objectclass=person)(sAMAccountName=tom)) | The syntax of userSearchFilter is standard [LDAP Search Filter Syntax](https://tools.ietf.org/search/rfc4515) |

This is intended as an example for further extension.
As is, extracting a static set of values from an incoming principal to create a dynamic search base is unlikely to be sufficient.
More important here is seeing what is possible and how it is integrated into Knox.

Here are some important references that provide useful background context.
* The [Apache Shiro Realm](http://shiro.apache.org/realm.html) documentation provides an overview of how Realms work in Shiro.
* The [Apahce Shiro JndiLdapRealm JavaDoc](https://shiro.apache.org/static/1.2.4/apidocs/org/apache/shiro/realm/ldap/JndiLdapRealm.html) documents the API used in this example.
* The [Apache Knox KnoxLdapRealm](https://github.com/apache/knox/blob/master/gateway-provider-security-shiro/src/main/java/org/apache/hadoop/gateway/shirorealm/KnoxLdapRealm.java) extends Shiro's JndiLdapRealm. This sample will extend KnoxLdapRealm.

Build the custom Realm
----------------------
This project can be built using the common maven commands.

    mvn clean install

Install custom Realm into Knox
------------------------------
The resulting JAR then needs to be copied into the Knox extension (ie ext) directory.

    cp target/knox-ldap-realm-sample-1.0-SNAPSHOT.jar <GATEWAY_HOME>/ext

Configure Knox to use custom Realm
----------------------------------
The topology configuration then needs to be updated to utilize the new feature.
 All of the changes below are within the authentication/ShiroProvider provider section.
 The first change is to the param main.ldapRealm setting that to the class name of the new Realm implementation: net.minder.knox.gateway.EyKnoxLdapRealm.
 After that you can see the additional changes in main.ldapRealm.principalRegex, main.ldapRealm.userSearchBase and main.ldapRealm.userSearchFilter.
 Note the required use of '&amp'; instead of simply '&' for main.ldapRealm.userSearchFilter when that value is used within XML.

```
<topology>

    <gateway>

        <provider>
            <role>authentication</role>
            <name>ShiroProvider</name>
            <enabled>true</enabled>
            <param>
                <name>sessionTimeout</name>
                <value>30</value>
            </param>
            <param>
                <name>main.ldapRealm</name>
                <value>net.minder.knox.EyKnoxLdapRealm</value>
            </param>
            <param>
                <name>main.ldapContextFactory</name>
                <value>org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory</name>
                <value>$ldapContextFactory</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory.url</name>
                <value>ldap://********:389</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
                <value>simple</value>
            </param>
            <param name="main.ldapRealm.contextFactory.systemUsername" value="CN=sam,CN=Users,DC=hwqe,DC=hortonworks,DC=com"/>
            <param name="main.ldapRealm.contextFactory.systemPassword" value="********"/>
            <param name="main.ldapRealm.principalRegex" value="(.*?)\\(.*)"/>
            <param name="main.ldapRealm.userSearchBase" value="CN={1},DC=hwqe,DC=hortonworks,DC=com"/>
            <param name="main.ldapRealm.userSearchFilter" value="(&amp;(objectclass=person)(sAMAccountName={2}))"/>
            <param>
                <name>urls./**</name>
                <value>authcBasic</value>
            </param>
        </provider>

        <provider>
            <role>identity-assertion</role>
            <name>Regex</name>
            <enabled>true</enabled>
            <param>
                <name>input</name>
                <value>(.*?)\\(.*)</value>
            </param>
            <param>
                <name>output</name>
                <value>{1}+{2}</value>
            </param>
        </provider>

        <provider>
            <role>hostmap</role>
            <name>static</name>
            <enabled>true</enabled>
            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>
        </provider>

    </gateway>

    <service>
        <role>NAMENODE</role>
        <url>hdfs://localhost:8020</url>
    </service>

    <service>
        <role>JOBTRACKER</role>
        <url>rpc://localhost:8050</url>
    </service>

    <service>
        <role>WEBHDFS</role>
        <url>http://localhost:50070/webhdfs</url>
    </service>

    <service>
        <role>WEBHCAT</role>
        <url>http://localhost:50111/templeton</url>
    </service>

    <service>
        <role>OOZIE</role>
        <url>http://localhost:11000/oozie</url>
    </service>

    <service>
        <role>WEBHBASE</role>
        <url>http://localhost:60080</url>
    </service>

    <service>
        <role>HIVE</role>
        <url>http://localhost:10001/cliservice</url>
    </service>

    <service>
        <role>RESOURCEMANAGER</role>
        <url>http://localhost:8088/ws</url>
    </service>

</topology>
```

Test
----
You can test this with a simple curl command.

    curl -iku 'Users\tom:p@ssw0rd' 'https://localhost:8443/gateway/sandbox/webhdfs/v1/?op=GETHOMEDIRECTORY'

Validate this by looking at the Knox gateway-audit.log.

    cat <GATEWWAY_HOME>/logs/gateway-audit.log

You should expect to see lines similar to the following:
Note that this log output is taken from the Knox 0.8.0 version so your output may vary.

```
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS||||access|uri|/gateway/sandbox/webhdfs/v1/?op=GETHOMEDIRECTORY|unavailable|Request method: GET
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|||authentication|uri|/gateway/sandbox/webhdfs/v1/?op=GETHOMEDIRECTORY|success|
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|||authentication|uri|/gateway/sandbox/webhdfs/v1/?op=GETHOMEDIRECTORY|success|Groups: []
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|Users+tom||identity-mapping|principal|Users\tom|success|Effective User: Users+tom
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|Users+tom||dispatch|uri|http://localhost:50070/webhdfs/v1/?op=GETHOMEDIRECTORY&user.name=Users%2Btom|unavailable|Request method: GET
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|Users+tom||dispatch|uri|http://localhost:50070/webhdfs/v1/?op=GETHOMEDIRECTORY&user.name=Users%2Btom|success|
16/03/15 17:41:51 ||28e2508e-1c0f-43f1-b211-369cee0969c1|audit|WEBHDFS|Users\tom|Users+tom||access|uri|/gateway/sandbox/webhdfs/v1/?op=GETHOMEDIRECTORY|success|
```