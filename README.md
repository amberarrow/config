# Config -- A Java class for dealing with configuration options.

## Overview

Config is a Java class that allows you to deal with configuration options from a variety
of sources such as a properties file, command line or programmatically assigned.

## Building

Most of the code is in the <b><strong>config.prop</b></strong> file. A sample application
is provided in <b><strong>TestConfig.java</b></strong>.
To build the sample application, just run:

    javac *.java

## Running

The sample application supports a number of options whose values are read from a Java
properties file (<b><strong>config.prop</b></strong>). You can dump these values with:

    java TestConfig

To change any of these configuration options from the command line, try something like:

    java TestConfig -host foo.com -port 1111 -debug true

You should see the modified values in the resulting dump. Option values can be checked
and validated in various ways. For example, if you used a nonsense value like xyz for the
debug option above, you'll see an exception.

## Contact

I appreciate feedback, so if you have any questions, suggestions or patches, please
send me email: amberarrow at gmail with the usual extension. Thanks.
