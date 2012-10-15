// Copyright 2007, 2008 Munagala V. Ramanath. All rights reserved.

// custom String option checker
class MyChkRelease implements Config.CheckVal<String> {
    String value;
    public String getValue() { return value; }

    public void check( final String v ) throws Exception {
        convertCheck( v );
    }

    public void convertCheck( final String v ) throws Exception {
        if ( null == v ) {
            value = null; return;
        }
        final String val = v.trim();
        if ( !(val.equals( "gold" ) || val.equals( "beta" )
               || val.equals( "alpha" )) )
            throw new Exception( "Invalid value" );
        value = v;
    }
}  // MyChkString

// test Config class
public class TestConfig {

    enum Options {HOST, PORT, NUM_PRODUCERS, NUM_QUEUES, NUM_MESSAGES,
            RATE, SPEED, DEBUG, RELEASE};
    enum Color {RED, BLUE};

    public static void main( String args[] ) throws Exception {
        // for testing:
        // -- error cases, set ERROR to true and uncomment one error case at a
        //    time in the 'else' part below
        // -- normal cases, set ERROR to false and comment out _ALL_ error cases
        //
        final boolean ERROR = false;
        if ( !ERROR ) {    // normal case
            final Config config = new Config( Options.class,
                                              Config.From.FROM_FILE,
                                              Config.From.FROM_CMDLINE,
                                              Config.From.DYNAMIC );

            config.initParam( Options.HOST, "host", String.class,
                              "localhost",
                              new Config.CheckString(),
                              Config.Settable.DYNAMIC,
                              Config.Importance.OPTIONAL );
            config.initParam( Options.PORT, "port", Integer.class,
                              5555, 
                              new Config.Check<Integer>( 1024, 65535 ),
                              Config.Settable.STATIC,
                              Config.Importance.REQUIRED );
            config.initParam( Options.DEBUG, "debug", Boolean.class,
                              true,
                              new Config.CheckBoolean(),
                              Config.Settable.DYNAMIC
                              // Importance defaults to OPTIONAL
                              );
            config.initParam( Options.NUM_PRODUCERS, "producers", Byte.class,
                              new Byte( (byte)10 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      new Byte( (byte)100 ) ),
                              Config.Settable.STATIC );
            config.initParam( Options.NUM_QUEUES, "queues", Short.class,
                              new Short( (short)20 ),
                              new Config.Check<Short>( Short.class ),
                              Config.Settable.STATIC );
            config.initParam( Options.NUM_MESSAGES, "messages", Long.class,
                              new Long( 1000000L ),
                              new Config.Check<Long>( null,
                                                      new Long( 5000000L ) ),
                              Config.Settable.DYNAMIC );
            config.initParam( Options.RATE, "rate", Float.class,
                              6.825f,
                              new Config.Check<Float>( Float.class ),
                              Config.Settable.DYNAMIC );
            config.initParam( Options.SPEED, "speed", Double.class,
                              5.56825e22,
                              new Config.Check<Double>( Double.class ),
                              Config.Settable.DYNAMIC );
            config.initParam( Options.RELEASE, "release", String.class,
                              "alpha",
                              new MyChkRelease(),
                              Config.Settable.STATIC,
                              Config.Importance.REQUIRED );

            System.out.println( "After initialization:" );
            config.dump();

            // parse properties file
            config.parseProp( "config.prop" );
            System.out.println( "After parsing file:" );
            config.dump();

            // parse command line arguments
            config.parseArgs( args );
            System.out.println( "After parsing command line:" );
            config.dump();

            // test retrieval of individual options
            final String  host      = config.get( Options.HOST );
            final Integer port      = config.get( Options.PORT );
            final Boolean debug     = config.get( Options.DEBUG );
            final Byte    producers = config.get( Options.NUM_PRODUCERS );
            final Short   queues    = config.get( Options.NUM_QUEUES );
            final Long    messages  = config.get( Options.NUM_MESSAGES );
            final Float   rate      = config.get( Options.RATE );
            final Double  speed     = config.get( Options.SPEED );
            final String  release   = config.get( Options.RELEASE );

            System.out.format( "Options.HOST --> %s%n"
                               + "Options.PORT --> %s%n"
                               + "Options.DEBUG --> %s%n"
                               + "Options.NUM_PRODUCERS --> %s%n"
                               + "Options.NUM_QUEUES --> %s%n"
                               + "Options.NUM_MESSAGES --> %s%n"
                               + "Options.RATE --> %f%n"
                               + "Options.SPEED --> %e%n"
                               + "Options.RELEASE --> %s%n",
                               host, port, debug, producers, queues, messages,
                               rate, speed, release );

            // dynamic options
            config.set( Options.NUM_MESSAGES, 99L );
            System.out.println( "NUM_MESSAGES is now: "
                                + config.get( Options.NUM_MESSAGES ) );

        } else {    // error cases
            // compile time errors

            // 1: initialize with a non-enum class
            /*
            final Config config = new Config( String.class );
            */

            // 2: mismatched value and type
            /*
            final Config config = new Config( Options.class );
            config.initParam( Options.HOST, "host", String.class,
                              1234,
                              new Config.CheckString(),
                              Config.Settable.DYNAMIC );
            */

            // 3: mismatched checker object and type
            /*
            final Config config = new Config( Options.class );
            config.initParam( Options.HOST, "host", String.class,
                              "localhost",
                              new Config.CheckBoolean(),
                              Config.Settable.DYNAMIC );
            */

            // run time errors

            // 1: initialize parameter with wrong enumeration
            /*
            final Config config = new Config( Options.class );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)22 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.DYNAMIC );
            */

            // 2: dynamic option with static config
            /*
            final Config config = new Config( Options.class );
            config.initParam( Options.PORT, "port", Byte.class,
                              new Byte ( (byte)22 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.DYNAMIC );
            */

            // 3: attempt to initialize from file with configuration not
            //    initialized to permit it.
            //
            /*
            final Config config = new Config( Options.class );
            config.initParam( Options.PORT, "port", Byte.class,
                              new Byte ( (byte)22 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.parseProp( "config.prop" );
            */

            // 4: attempt to initialize from commandline with configuration not
            //    initialized to permit it.
            //
            /*
            final Config config = new Config( Color.class,
                                              Config.From.FROM_FILE );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)22 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.initParam( Color.BLUE, "red", Boolean.class,
                              false,
                              new Config.CheckBoolean(),
                              Config.Settable.STATIC );
            config.parseProp( "config.prop" );
            config.parseArgs( args );
            */

            // 5: attempt to initialize from command line before file
            /*
            final Config config = new Config( Color.class,
                                        Config.From.FROM_FILE,
                                        Config.From.FROM_CMDLINE );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)22 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.initParam( Color.BLUE, "red", Boolean.class,
                              false,
                              new Config.CheckBoolean(),
                              Config.Settable.STATIC );
            config.parseArgs( args );
            config.parseProp( "config.prop" );
            */

            // 6: attempt to initialize with a value outside permitted range
            /*
            final Config config = new Config( Color.class,
                                              Config.From.FROM_FILE );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)0 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.parseProp( "config.prop" );
            */

            // 7: attempt to dynamically initialize a parameter that does not
            //    allow it
            //
            /*
            final Config config = new Config( Color.class,
                                              Config.From.DYNAMIC );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)8 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.set( Color.RED, (byte)99 );
            */

            // 8: attempt to initialize same parameter more than once
            //
            /*
            final Config config = new Config( Color.class,
                                              Config.From.DYNAMIC );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)8 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)9 ),
                              new Config.Check<Byte>( new Byte( (byte)2 ),
                                                      new Byte( (byte)16 ) ),
                              Config.Settable.STATIC );
            */

            // 9: attempt to read from file before initializing all parameters
            //
            /*
            final Config config = new Config( Color.class,
                                              Config.From.FROM_FILE );
            config.initParam( Color.RED, "red", Byte.class,
                              new Byte ( (byte)8 ),
                              new Config.Check<Byte>( new Byte( (byte)1 ),
                                                      Byte.MAX_VALUE ),
                              Config.Settable.STATIC );
            config.parseProp( "config.prop" );
            */
        }  // if
    }  // main
}
