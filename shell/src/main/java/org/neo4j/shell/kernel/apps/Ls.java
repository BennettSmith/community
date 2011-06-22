/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.shell.kernel.apps;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.impl.util.SingleNodePath;
import org.neo4j.shell.App;
import org.neo4j.shell.AppCommandParser;
import org.neo4j.shell.OptionDefinition;
import org.neo4j.shell.OptionValueType;
import org.neo4j.shell.Output;
import org.neo4j.shell.Session;
import org.neo4j.shell.ShellException;

/**
 * Mimics the POSIX application with the same name, i.e. lists
 * properties/relationships on a node or a relationship.
 */
@Service.Implementation( App.class )
public class Ls extends ReadOnlyGraphDatabaseApp
{
    /**
     * Constructs a new "ls" application.
     */
    public Ls()
    {
        super();
        this.addOptionDefinition( "b", new OptionDefinition( OptionValueType.NONE,
            "Brief summary instead of full content" ) );
        this.addOptionDefinition( "v", new OptionDefinition( OptionValueType.NONE,
            "Verbose mode" ) );
        this.addOptionDefinition( "q", new OptionDefinition( OptionValueType.NONE, "Quiet mode" ) );
        this.addOptionDefinition( "p", new OptionDefinition( OptionValueType.NONE,
            "Lists properties" ) );
        this.addOptionDefinition( "r", new OptionDefinition( OptionValueType.NONE,
            "Lists relationships" ) );
        this.addOptionDefinition( "f", new OptionDefinition( OptionValueType.MUST,
            "Filters property keys/values and relationship types. Supplied either as a single value " +
            "or as a JSON string where both keys and values can contain regex. " +
            "Starting/ending {} brackets are optional. Examples:\n" +
            "  \"username\"\n\tproperty/relationship 'username' gets listed\n" +
            "  \".*name:ma.*, age:''\"\n\tproperties with keys matching '.*name' and values matching 'ma.*' " +
            "gets listed,\n\tas well as the 'age' property. Also relationships matching '.*name' or 'age'\n\tgets listed\n" +
            "  \"KNOWS:out,LOVES:in\"\n\toutgoing KNOWS and incoming LOVES relationships gets listed" ) );
        this.addOptionDefinition( "i", new OptionDefinition( OptionValueType.NONE,
            "Filters are case-insensitive (case-sensitive by default)" ) );
        this.addOptionDefinition( "l", new OptionDefinition( OptionValueType.NONE,
            "Filters matches more loosely, i.e. it's considered a match if just " +
            "a part of a value matches the pattern, not necessarily the whole value" ) );
    }

    @Override
    public String getDescription()
    {
        return "Lists the contents of the current node or relationship. " +
        	"Optionally supply\n" +
            "node id for listing a certain node using \"ls <node-id>\"";
    }

    @Override
    protected String exec( AppCommandParser parser, Session session,
        Output out ) throws ShellException, RemoteException
    {
        boolean brief = parser.options().containsKey( "b" );
        boolean verbose = parser.options().containsKey( "v" );
        boolean quiet = parser.options().containsKey( "q" );
        if ( verbose && quiet )
        {
            verbose = false;
            quiet = false;
        }
        boolean displayProperties = parser.options().containsKey( "p" );
        boolean displayRelationships = parser.options().containsKey( "r" );
        boolean caseInsensitiveFilters = parser.options().containsKey( "i" );
        boolean looseFilters = parser.options().containsKey( "l" );
        Map<String, Object> filterMap = parseFilter( parser.options().get( "f" ), out );
        if ( !displayProperties && !displayRelationships )
        {
            displayProperties = true;
            displayRelationships = true;
        }

        NodeOrRelationship thing = null;
        if ( parser.arguments().isEmpty() )
        {
            thing = this.getCurrent( session );
        }
        else
        {
            thing = NodeOrRelationship.wrap( this.getNodeById( Long
                .parseLong( parser.arguments().get( 0 ) ) ) );
        }

        if ( displayProperties )
        {
            displayProperties( thing, out, verbose, quiet, filterMap, caseInsensitiveFilters,
                    looseFilters, brief );
        }
        if ( displayRelationships )
        {
            if ( thing.isNode() )
            {
                displayRelationships( thing, session, out, verbose, quiet,
                        Direction.BOTH, filterMap, caseInsensitiveFilters, looseFilters, brief );
            }
            else
            {
                displayNodes( parser, thing, session, out );
            }
        }
        return null;
    }

    private void displayNodes( AppCommandParser parser, NodeOrRelationship thing,
            Session session, Output out ) throws RemoteException, ShellException
    {
        Relationship rel = thing.asRelationship();
        out.println( getDisplayName( getServer(), session, rel.getStartNode(), false ) +
                " --" + getDisplayName( getServer(), session, rel, true, false ) + "-> " +
                getDisplayName( getServer(), session, rel.getEndNode(), false ) );
    }

    private Iterable<String> sortKeys( Iterable<String> source )
    {
        List<String> list = new ArrayList<String>();
        for ( String item : source )
        {
            list.add( item );
        }
        Collections.sort( list, new Comparator<String>()
        {
            public int compare( String item1, String item2 )
            {
                return item1.toLowerCase().compareTo( item2.toLowerCase() );
            }
        } );
        return list;
    }

    private Map<String, Collection<Relationship>> readAllRelationships(
            Iterable<Relationship> source )
    {
        Map<String, Collection<Relationship>> map =
            new TreeMap<String, Collection<Relationship>>();
        for ( Relationship rel : source )
        {
            String type = rel.getType().name().toLowerCase();
            Collection<Relationship> rels = map.get( type );
            if ( rels == null )
            {
                rels = new ArrayList<Relationship>();
                map.put( type, rels );
            }
            rels.add( rel );
        }
        return map;
    }
    
    private void displayProperties( NodeOrRelationship thing, Output out,
        boolean verbose, boolean quiet, Map<String, Object> filterMap,
        boolean caseInsensitiveFilters, boolean looseFilters, boolean brief )
        throws RemoteException
    {
        int longestKey = findLongestKey( thing );
        int count = 0;
        for ( String key : sortKeys( thing.getPropertyKeys() ) )
        {
            Object value = thing.getProperty( key );
            if ( !filterMatches( filterMap, caseInsensitiveFilters, looseFilters, key, value ) )
            {
                continue;
            }

            count++;
            if ( !brief )
            {
                StringBuilder builder = new StringBuilder();
                builder.append( "*" + key );
                if ( !quiet )
                {
                    builder.append( multiply( " ", longestKey - key.length() + 1 ) );
                    builder.append( "=" + format( value, true ) );
                    if ( verbose )
                    {
                        builder.append( " (" + getNiceType( value ) + ")" );
                    }
                }
                out.println( builder.toString() );
            }
        }
        if ( brief )
        {
            out.println( "Property count: " + count );
        }
    }

    private void displayRelationships( NodeOrRelationship thing,
        Session session, Output out, boolean verbose, boolean quiet, Direction direction,
        Map<String, Object> filterMap, boolean caseInsensitiveFilters,
        boolean looseFilters, boolean brief ) throws ShellException, RemoteException
    {
        RelationshipExpander expander = toExpander( getServer().getDb(), direction, filterMap,
                caseInsensitiveFilters, looseFilters );
        Path nodeAsPath = new SingleNodePath( thing.asNode() );
        Map<String, Collection<Relationship>> relationships =
                readAllRelationships( expander != null ? expander.expand( nodeAsPath ) : Collections.<Relationship>emptyList() );
        Node node = thing.asNode();
        for ( Map.Entry<String, Collection<Relationship>> entry : relationships.entrySet() )
        {
            if ( brief )
            {
                Relationship firstRel = entry.getValue().iterator().next();
                String relDisplay = withArrows( firstRel, getDisplayName( getServer(), session,
                        firstRel, false, true ), thing.asNode() );
                out.println( getDisplayName( getServer(), session, thing, true ) + relDisplay +
                        " x" + entry.getValue().size() );
            }
            else
            {
                for ( Relationship rel : entry.getValue() )
                {
                    StringBuffer buf = new StringBuffer( getDisplayName(
                            getServer(), session, thing, true ) );
                    String relDisplay = quiet ? "" : getDisplayName( getServer(), session, rel, verbose, true );
                    buf.append( withArrows( rel, relDisplay, thing.asNode() ) );
                    buf.append( getDisplayName( getServer(), session, rel.getOtherNode( node ), true ) );
                    out.println( buf );
                }
            }
        }
    }

    private static String getNiceType( Object value )
    {
        return Set.getValueTypeName( value.getClass() );
    }

    private static int findLongestKey( NodeOrRelationship thing )
    {
        int length = 0;
        for ( String key : thing.getPropertyKeys() )
        {
            if ( key.length() > length )
            {
                length = key.length();
            }
        }
        return length;
    }
}