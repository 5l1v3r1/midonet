## Organization of the project

The **MidoNet** project is split into 4 submodules:

### midokura-util

It contains basic utilities used by the other modules. For a more detailed
explanation please consult the following file.

Description of the module [contents](docs/midokura-util.md).

### midolman

Contains the *MidoNet* edge controller code.

Description of the module [contents](docs/midolman.md).

### midonet-api

Contains the implementation of the *MidoNet* REST API.

### midonet-functional-tests

Contains a set of functional tests that validate the basic functionality of *MidoNet*.

## Building the project
### Complete build

    ~/midonet$ mvn

This will build all the modules while running all the tests from all the modules.
This requires that the *functional tests environment* is already set-up on the machine.

### Complete build (skipping the tests)

    ~/midonet$ mvn -DskipTests

This will build all the modules while skipping all the tests from the modules.

### Build all & Run functional tests

    ~/midonet$ mvn -DjustFunctionalTests clean test

This will build all the modules but only run the tests from the
midonet-functional-tests module.

### Build all & Run a functional test

    ~/midonet$ mvn -DjustFunctionalTests test -Dtest=BridgeTest

This will build all the modules but only run the tests from the
midonet-functional-tests module.

If you run tests with the embedded version of Midolman, you need to run them as root because Midolman opens a Netlink socket:

    ~/midonet$ sudo mvn -Dmaven.repo.local=/home/midokura/.m2/repository/ test -Dtest=YourTest -DfailIfNoTests=false

If the test launches Midolman out of process (e.g. some functional tests), then password-less sudo for /usr/bin/java must be enabled for your user.
The functional tests also create interfaces and modify them, so password-less sudo for /sbin/ip must also be enabled for your user.

### Build all & Run tests (but skip the functional tests)

    ~/midonet$ mvn -DskipFunctionalTests clean test

This will build all the modules and run all the test (but it will skip all the
functional tests)

### Running with a remotely managed host.

In order to run a local controller one would need to provide a file named
*managed_host.properties* somewhere in the classpath of the running process
(being it a test case or midolman).

This file has the following syntax:

    [remote_host]
    specification = <user>:<pass>@<host>:<port>

    [forwarded_local_ports]
    12344 = 12344   # OpenvSwitch database connection port
    9090 = 9090     # Midonet Sudo Helper connection
    6640 = 6640     #

    [forwarded_remote_ports]
    6650 = 6650     # 1'st midolman controller connection
    6655 = 6655     # 2'nd midolman controller connection

    [midonet_privsep]
    path = <remote_target_path>/midonet-privsep

Everything except the \[remote_host\] section is optional.
Midolman at the beginning will try to see of such a file exists
(via a call to RemoteHost.getSpecification().isValid()) and if such a file exists
then the RemoteHost class will parse the file and setup the required port
forwardings over a new ssh session to the remote_host.specification address.

Each call to ProcessHelper.newProcess() will check to see if a specification has
been read already and if that specificaiton is valid it will execute all the
calls via a ssh tunnel instead of using a local Process.getRuntime().exec() call.

The port fowarding ensure that the local controller can communicate with remote
services and viceversa and the remote execution ensure that what we want to be
executed and needs access to the remote machine actually has access to it by the
virtue of being executed remotely.

## Subsystems documentation

### Metrics & Monitoring subsystem.

See the [Metrics & Monitoring document](docs/monitoring.md) for details on the
metrics monitoring subsystem.

## Intellij Tips
If you use Intellij the following variables can be useful
* $PROJECT_DIR$
* $APPLICATION_HOME_DIR$
* $MODULE_DIR$
* $USER_HOME$
