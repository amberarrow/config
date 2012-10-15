// Copyright 2007 Munagala V. Ramanath. All rights reserved.

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.BitSet;
import java.util.TreeMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import java.io.File;
import java.io.FileInputStream;

// Class for configuring an application. Not thread safe.
//
// 1. Each configuration item is defined by an enumeration literal and has a
//    corresponding value. The values must be of one of the following types:
//    Byte, Short, Integer, Long, Boolean, Float, Double, String
// 2. The configuration is initialized in 3 ways:
//    (a) default value
//    (b) from a properties file
//    (c) from command line parameters
// 3. Configuration values can also be changed dynamically.
//
// Typical usage is as follows (see TestConfig.java for details):
//
// 1. Create an enum listing all parameters.
// 2. Create a new Config object with this enum and the desired initialization
//    sources (e.g. file, command line, dynamic -- see 'From' enum below).
// 3. Call initParam() once for each parameter to initialize it, i.e. specify
//    its type, default value, valid range, etc.
// 4. Call parseProp() if you have a properties file.
// 5. Call parseArgs() if you want command line arguments read.
// 6. Call get( <enum literal> ) to retrieve value of any parameter and
//    set( <enum literal>, value ) to set values of dynamic parameters.
// 7. Call dump() to dump the entire configuration to standard output.
// 
public class Config {

    // how a parameter got its value
    public static enum ValueSource {DEFAULT, COMMAND_LINE, FILE, DYNAMIC};

    // whether a parameter is required or not
    public static enum Importance {REQUIRED, OPTIONAL};

    // current state of config
    // START        -- not yet initialized
    // INITIALIZED  -- intialized names and default values for all parameters
    // DONE_FILE    -- initialized from properties file
    // DONE_CMDLINE -- initialized from commandline parameters
    //
    public static enum State {START, INITIALIZED, DONE_FILE, DONE_CMDLINE};

    // whether individual parameters can be changed dynamically
    public static enum Settable {STATIC, DYNAMIC};

    // allowed initializations for entire configuration
    public static enum From {FROM_FILE, FROM_CMDLINE, DYNAMIC};
    protected final BitSet initFrom = new BitSet( 8 );

    // no. of required parameters
    protected int cntRequired = 0;

    // check validity of parameter value
    public static interface CheckVal<T> {
        T getValue();
        // check value for validity
        void check( final T val ) throws Exception;
        // convert argument into appropriate type and check it
        void convertCheck( final String val )
            throws Exception;
    }

    // String specialization of CheckVal
    public static class CheckString implements CheckVal<String> {
        String value;
        public String getValue() { return value; }
        public void check( final String val )
            throws Exception {
            convertCheck( val );
        }
        public void convertCheck( final String val )
                throws Exception {
            if ( null == val ) {
                value = null;
                return;
            }
            final String v = val.trim();
            if ( v.length() == 0 )
                throw new Exception( "value is blank" );
            if ( v.length() != val.length() )
                throw new Exception( "value has leading/"+
                                     "trailing blanks" );
            value = v;
        }
    }  // CheckString

    // Boolean specialization of CheckVal
    public static class CheckBoolean implements CheckVal<Boolean> {
        Boolean value;
        public Boolean getValue() { return value; }
        public void check( final Boolean val )
            throws Exception {
            value = val;
        }  // check
        public void convertCheck( final String val )
                throws Exception {
            if ( null == val ) {
                value = null;
                return;
            }
            final String v = val.trim();
            if ( v.length() == 0 )
                throw new Exception( "value is blank" );
            if ( v.length() != val.length() )
                throw new Exception( "value has leading/"+
                                     "trailing blanks" );
            boolean iVal;
            if ( "true".equalsIgnoreCase( v ) ) {
                iVal = true;
            } else if ( "false".equalsIgnoreCase( v ) ) {
                iVal = false;
            } else {
                throw new Exception( "Invalid Boolean: '" + v + "'" );
            }
            check( iVal );
        }  // convertCheck
    }  // CheckBoolean

    // generic implementation of CheckVal
    // the template parameter must implement compareTo()
    public static class Check<T extends Number> implements CheckVal<T> {
        T value;
        private final T         MIN, MAX;
        private final Class<?>  vClass;
        private final Method    compare;

        public T getValue() { return value; }

        public Check( final Class<T> cls ) throws Exception {
            vClass = cls; MIN = null; MAX = null;
            compare = vClass.getMethod( "compareTo", vClass );
        }

        public Check( final T aMin, final T aMax ) throws Exception {
            Class<?> cls = null;
            if ( null != aMin ) {
                cls = aMin.getClass();
            } else if ( null != aMax ) {
                cls = aMax.getClass();
            } else {
                throw new Exception( "min and max are both null" );
            }
            vClass  = cls;
            compare = vClass.getMethod( "compareTo", vClass );
            MIN = aMin; MAX = aMax;
        }  // ctor

        // check range constraints if any
        public void check( final T val ) throws Exception {
            if ( null == val ) {
                value = null;
                return;
            }

            if ( null != MIN ) {
                final int result = (Integer)compare.invoke( val, MIN );
                if ( result < 0 )
                    throw new Exception( "value too small: " + val
                                         + "; must be >= " + MIN );
            }
            if ( null != MAX ) {
                final int result = (Integer)compare.invoke( val, MAX );
                if ( result > 0 )
                    throw new Exception( "value too large: " + val
                                         + "; must be <= " + MAX );
            }
            value = val;
        }  // check

        // parse value from string and check it
        public void convertCheck( final String val )
            throws Exception {
            if ( null == val )
                throw new Exception( "value is null" );
            final String v = val.trim();
            if ( v.length() == 0 )
                throw new Exception( "value is blank" );
            if ( v.length() != val.length() )
                throw new Exception( "value has leading/"+
                                     "trailing blanks" );
            final Constructor ctor
                = vClass.getConstructor( String.class );

            // this yields compile time diagnostic:
            //final T nVal = value.getClass().cast( ctor.newInstance( v ) );
            final T nVal = (T)ctor.newInstance( v );

            check( nVal );
        }  // convertCheck
    }  // Check

    // wraps values
    static protected class Value {

        Object           value;       // param value (may change if dynamic)
        ValueSource      source;      // param source (may change)
        final CheckVal   checkVal;    // value checker
        final Class      type;        // param type (String, Boolean, etc.)
        final Importance importance;  // param required or not
        final Settable   settable;    // whether dynamic

        Value( final Object      val,
               final CheckVal    cv,
               final Class       tp,
               final ValueSource src,
               final Importance  imp,
               final Settable    st ) {
            value = val; checkVal = cv; type = tp; source = src;
            importance = imp; settable = st;
        }
    }  // Value

    // current state
    private State state = State.START;

    // class and size of enumeration
    final Class keyClass;
    final int nParams;

    // maps of enumeration-to-value and string-to-enumeration.
    // want a predictable order when we traverse these, so use TreeMap
    //
    final private TreeMap<Enum,Value> map;
    final private TreeMap<String,Enum> nameMap;

    // eClass:
    //    Class object of enum defining parameter set
    // from:
    //    Zero or more flags: FROM_FILE, FROM_CMDLINE, DYNAMIC identifying
    //    permitted initialization sources for entire configuration. If no
    //    flags are given, initialization will be allowed only via initParam()
    //    calls. If dynamic modification of parameters is needed, the DYNAMIC
    //    flag must be supplied and also toggled on the individual parameters
    //    via initParam().
    //
    public <T extends Enum<T>> Config( final Class<T> eClass,
                                       final From... from )
            throws Exception {

        final Enum keys[] = (Enum[])eClass.getEnumConstants();
        if ( null == keys )
            throw new Exception( "Class " + eClass.getName()
                                 + " is not an enum" );
        if ( 0 == keys.length )
            throw new Exception( "Class " + eClass.getName()
                                 + " has no enum constants" );

        keyClass = eClass;
        nParams  = keys.length;
        nameMap  = new TreeMap<String,Enum>();
        map      = new TreeMap<Enum,Value>();

        // process source flags
        if ( 0 == from.length )
            return;
        for ( From f : from ) {
            final int idx = f.ordinal();
            if ( initFrom.get( idx ) )
                throw new Exception( "Duplicate flag: " + f );
            initFrom.set( idx );
        }
    }  // ctor

    // check type and value
    public void check( final Class type, final Object v ) throws Exception {
        if ( null == v )
            return;
        try {
            type.cast( v );
        } catch( ClassCastException e ) {
            throw new Exception( "type mismatch: v is " + v.getClass().getName()
                                 + ", but type is " + type.getName() );
        }
    }  // check

    // Initialize a parameter
    //
    // key:
    //    Enumerator identifying parameter, e.g. Dir.NORTH
    // prop:
    //    Property name as found in property file, e.g. inFile. A hyphen is
    //    prepended to deduce the corresponding command line parameter
    // type:
    //    Class object indicating the value type (we can deduce this
    //    from v except when v is null)
    // v:
    //    Default value; may be null which implies the parameter is not
    //    initialized
    // checkVal:
    //    object for checking value; must not be null
    // settable:
    //    Whether dynamically settable; if this is DYNAMIC, the configuration
    //    must have been constructed with the From.DYNAMIC flag
    // req:
    //    Optional argument indicating if parameter is required
    //
    public <V> void initParam( final Enum         key,
                               final String       prop,
                               final Class        type,
                               final V            v,
                               final CheckVal<V>  checkVal,
                               final Settable     settable,
                               final Importance... req ) throws Exception {
        // cannot add a new parameter if properties file or command line has
        // already been parsed
        if ( State.START != state )
            throw new Exception( key.name() + " already initialized" );

        // check if dynamic flag is set
        if ( Settable.DYNAMIC == settable
                 && !initFrom.get( From.DYNAMIC.ordinal() ) )
            throw new Exception( key.name()
                                 + " is dynamic but configuration is not" );

        // check if this parameter is already initialized
        final Value curVal = map.get( key );
        if ( null != curVal )
            throw new Exception( key.name()
                                 + " already initialized" );

        // check that property name is valid
        if ( null == prop )
            throw new Exception( "Property name for " + key + " is null" );
        final String propName = prop.trim();
        if ( prop.length() > propName.length() )
            throw new Exception( "Property name for " + key
                                 + " has leading/trailing blanks" );

        // check type and value
        check( type, v );
        if ( req.length > 1 )
            throw new Exception( "Too many parameters" );

        // check that key is from our enum
        check( keyClass, key );

        // value checker must not be null
        if ( null == checkVal )
            throw new Exception( "Value checker must not be null" );

        // finally, check the value itself and insert in maps
        checkVal.check( v );
        final Importance imp = req.length == 1 ? req[ 0 ] : Importance.OPTIONAL;
        final Value val = new Value( v, checkVal, type, ValueSource.DEFAULT,
                                     imp, settable );
        nameMap.put( propName, key );
        map.put( key, val );

        // check if all parameters have been initialized and set state
        if ( map.size() == nParams ) {
            state = State.INITIALIZED;
        }
    }  // initParam

    // check that all required arguments are present
    public void checkRequired() throws Exception {
        // initialization must be complete
        if ( State.START == state )
            throw new Exception( "Must initialize all parameters" );

        for ( Map.Entry<Enum,Value> entry : map.entrySet() ) {
            final Value val = entry.getValue();
            if ( Importance.REQUIRED == val.importance && null == val.value ) {
                final String name = entry.getKey().name();
                throw new Exception( "required parameter "
                                     + name + " not initialized" );
            }
        }
    }  // checkRequired

    // parse command line arguments; 
    //
    // -- all parameters must be initialized before this method is called.
    // -- since commandline values should override values in the properties
    //    file, parseProp should be called before parseArgs
    //
    public void parseArgs( final String args[] ) throws Exception {
        // check if command line parsing is allowed
        if ( !initFrom.get( From.FROM_CMDLINE.ordinal() ) )
            throw new Exception( "Command line initialization not permitted "
                + "for this configuration");

        // check if all parameters are initialized
        if ( State.INITIALIZED != state && State.DONE_FILE != state )
            throw new Exception( State.START == state
                ? "Must initialize parameters before parsing command line"
                : "Command line arguments already parsed" );

        // check if properties file has been parsed
        if ( initFrom.get( From.FROM_FILE.ordinal() )
                 && State.INITIALIZED == state )
            throw new Exception( "Parsing command line must occur "
                + "after parsing properties file");

        int nRequired = 0;
        if ( args.length > 0 ) {
            for ( int i = 0; i < args.length; ++i ) {
                String keyStr = args[ i ].trim();
                if ( 0 == keyStr.length() )
                    continue;
                if ( '-' != keyStr.charAt( 0 ) )
                    throw new Exception( "parameter: " + keyStr
                                         + " must start with hypen" );
                keyStr = keyStr.substring( 1 );  // skip leading hypen
                if ( 0 == keyStr.length() )
                    continue;
                final Enum key = nameMap.get( keyStr );
                if ( null == key )
                    throw new Exception( "unknown parameter: " + keyStr );

                Value val = map.get( key );
                CheckVal chk = val.checkVal;

                ++i;
                if ( args.length == i )
                    throw new Exception( "Missing arg for: " + keyStr );

                chk.convertCheck( args[ i ] );
                if ( null == chk.getValue() )
                    throw new Exception( "Value for " + keyStr
                                         + " must not be null" );

                val.value = chk.getValue();
                val.source = ValueSource.COMMAND_LINE;
                if ( Importance.REQUIRED == val.importance ) {
                    ++nRequired;
                }
            }  // for
        }

        // check that all required arguments are present
        if ( nRequired < cntRequired ) {
            checkRequired();
        }
        state = State.DONE_CMDLINE;
    }  // parseArgs

    // parse properties file
    // 
    // -- all parameters must be initialized before this method is called
    // -- since commandline values should override values in the properties
    //    file, parseProp should be called before parseArgs
    //
    public void parseProp( final String fName ) throws Exception {
        if ( !initFrom.get( From.FROM_FILE.ordinal() ) )
            throw new Exception( "Initialization from file not permitted "
                + "for this configuration");
        if ( State.INITIALIZED != state )
            throw new Exception( State.START == state
                ? "Must initialize ALL params before reading properties file"
                : State.DONE_FILE == state
                ? "Properties file already parsed"
                : "Properties file must be parsed before command line" );

        if ( null == fName )
            throw new Exception( "file name is null" );
        final String name = fName.trim();
        if ( 0 == name.length() )
            throw new Exception( "file name is blank" );
        final File propFile = new File( name );
        if ( ! propFile.exists() )
            throw new Exception( "file does not exist: " + name );
        if ( ! propFile.canRead() )
            throw new Exception( "file not readable: " + name );

        final Properties prop = new Properties();
        prop.load( new FileInputStream( propFile ) );

        // initialize parameters
        int nRequired = 0,   // no. of required options initialized
            nProcessed = 0;  // no. of properties from file actually used
        for ( Map.Entry<String,Enum> entry : nameMap.entrySet() ) {
            // note that key does not have leading hyphen
            final String key = entry.getKey();
            final String newValue = prop.getProperty( key );
            if ( null == newValue )
                continue;    // parameter not present in file

            final Value curValue = map.get( entry.getValue() );
            final CheckVal chk = curValue.checkVal;
            chk.convertCheck( newValue );
            if ( null == chk.getValue() )
                throw new Exception( "Value for " + key + " must not be null" );

            curValue.value = chk.getValue();
            curValue.source = ValueSource.FILE;
            if ( Importance.REQUIRED == curValue.importance ) {
                ++nRequired;
            }
            ++nProcessed;
        }

        // check that all required arguments are present
        if ( nRequired < cntRequired ) {
            checkRequired();
        }
        if ( prop.size() > nProcessed ) {
            System.out.println( "Warning: " + (prop.size() - nProcessed)
                                + " properties from file '" + name
                                + "' not used, nProcessed = " + nProcessed );
        }
        state = State.DONE_FILE;
    }  // parseProp

    // check key is from our enum
    protected void checkKey( final Enum key ) throws Exception {
        try {
            keyClass.cast( key );
        } catch( ClassCastException e ) {
            throw new Exception( "type mismatch: key is "
                                 + key.getClass().getName()
                                 + ", but key type is " + keyClass.getName() );
        }
    }  // checkKey

    // get value of a parameter; throws exception if parameter is not yet
    // initialized
    //
    public <V> V get( final Enum key ) throws Exception {
        checkKey( key );
        final Value value = map.get( key );
        if ( null == value )
            throw new Exception( key + " not yet initialized" );
        return (V)value.value;
    }  // get

    // set value of a parameter
    // 1. Parameter must be dynamically settable and initialization from
    //    properties file and command line must be complete before this method
    //    can be called
    // 2. Throws exception if parameter is not yet initialized
    //
    public <V> void set( final Enum key, final V value ) throws Exception {
        checkKey( key );
        final Value curVal = map.get( key );
        if ( null == curVal )
            throw new Exception( key + " not yet initialized" );
        if ( Settable.STATIC == curVal.settable )
            throw new Exception( key + " not dynamic" );

        // check if initialization, parsing properties file and command line is
        // complete
        //
        if ( State.START == state )
            throw new Exception( "Must initialize parameters before dynamic "
                                 + "assignment" );
        if ( initFrom.get( From.FROM_CMDLINE.ordinal() )
             && State.DONE_CMDLINE != state )
            throw new Exception( "Must parse command line before dynamic "
                                 + "assignment" );
        if ( initFrom.get( From.FROM_FILE.ordinal() )
             && State.DONE_CMDLINE != state && State.DONE_FILE != state )
            throw new Exception( "Must parse properties file before dynamic "
                                 + "assignment" );

        // check that value has correct type and satisfies any range constraints
        check( curVal.type, value );
        curVal.checkVal.check( value );

        // finally, set value and source flag
        curVal.value = curVal.checkVal.getValue();
        curVal.source = ValueSource.DYNAMIC;
    }  // set

    // dump all parameters and their values
    public void dump() throws Exception {
        // initialization must be complete
        if ( State.START == state )
            throw new Exception( "Cannot dump before initialization complete" );

        // dumped in key (enum) order
        for ( Map.Entry<Enum,Value> entry : map.entrySet() ) {
            final Enum key = entry.getKey();
            final Value val = entry.getValue();
            System.out.format( "    %s='%s', %s, %s, %s%n", key.name(),
                               val.value, val.importance, val.source,
                               val.settable );
        }
    }  // dump

    // for testing (see TestConfig.java for more elaborate testing)
    enum Stuff {RED, BLUE, GREEN, BLACK};
    enum Abc {A, B, C};

    public static void main( String args[] ) throws Exception {
        Stuff color;
        Config config = new Config( Stuff.class,
                                    From.FROM_FILE,
                                    From.FROM_CMDLINE,
                                    From.DYNAMIC );
        config.initParam( Stuff.RED, "abc", String.class, "bright red",
                          new CheckString(), Settable.STATIC,
                          Importance.REQUIRED );

        config.initParam( Stuff.BLUE, "xyz", Integer.class, 5, 
                          new Check<Integer>( 1, 10 ), Settable.DYNAMIC,
                          Importance.OPTIONAL );

        config.initParam( Stuff.GREEN, "ppp", Boolean.class, true,
                          new CheckBoolean(),
                          Settable.DYNAMIC );

        config.initParam( Stuff.BLACK, "black", Long.class, 0L,
                          new Check<Long>( Long.class ),
                          Settable.DYNAMIC );

        System.out.println( "Initialized" );
        String val1 = config.get( Stuff.RED );
        Integer val2 = config.get( Stuff.BLUE );
        Boolean val3 = config.get( Stuff.GREEN );
        System.out.println( "Stuff.RED --> " + val1 + ", Stuff.BLUE --> " + val2
                            + ", Stuff.GREEN --> " + val3 );

        config.dump();

        // errors
        //Abc x = config.get( Abc.A );
        //config.initParam( Abc.A, "aaa", 55 );
        //Config cfg = new Config( Config.class );
    }  // main
}
