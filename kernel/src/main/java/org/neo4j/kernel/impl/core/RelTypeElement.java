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
package org.neo4j.kernel.impl.core;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.neo4j.kernel.impl.util.RelIdArray;
import org.neo4j.kernel.impl.util.RelIdArray.DirectionWrapper;
import org.neo4j.kernel.impl.util.RelIdArray.RelIdIterator;

class RelTypeElement extends RelTypeElementIterator
{
    private final RelIdArray src;
    private final Set<Long> remove = new HashSet<Long>();
    private final RelIdIterator srcIterator;
    private final RelIdIterator addIterator;
    private RelIdIterator currentIterator;
    private long nextElement;
    private boolean nextElementDetermined;
    private final DirectionWrapper direction;

    static RelTypeElementIterator create( String type, NodeImpl node,
            RelIdArray src, RelIdArray add, RelIdArray remove, DirectionWrapper direction )
    {
        if ( add == null && remove == null )
        {
            return new FastRelTypeElement( type, node, src, direction );
        }
        return new RelTypeElement( type, node, src, add, remove, direction );
    }

    private RelTypeElement( String type, NodeImpl node, RelIdArray src,
            RelIdArray add, RelIdArray remove, DirectionWrapper direction )
    {
        super( type, node );
        this.direction = direction;
        if ( src == null )
        {
            src = RelIdArray.EMPTY;
        }
        this.src = src;
        this.srcIterator = src.iterator( direction );
        this.addIterator = add == null ? RelIdArray.EMPTY.iterator( direction ) : add.iterator( direction );
        if ( remove != null )
        {
            for ( RelIdIterator iterator = remove.iterator( DirectionWrapper.BOTH ); iterator.hasNext(); )
            {
                this.remove.add( iterator.next() );
            }
        }
        this.currentIterator = srcIterator;
    }

    public boolean hasNext( NodeManager nodeManager )
    {
        if ( nextElementDetermined )
        {
            return nextElement != -1;
        }
        
        while ( currentIterator.hasNext() || currentIterator != addIterator )
        {
            while ( currentIterator.hasNext() )
            {
                long value = currentIterator.next();
                if ( !remove.contains( value ) )
                {
                    nextElement = value;
                    nextElementDetermined = true;
                    return true;
                }
            }
            currentIterator = addIterator;
        }
        nextElementDetermined = true;
        nextElement = -1;
        return false;
    }

    public long next( NodeManager nodeManager )
    {
        if ( !hasNext( nodeManager ) )
        {
            throw new NoSuchElementException();
        }
        nextElementDetermined = false;
        return nextElement;
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }
    
    public boolean isSrcEmpty()
    {
        return src.isEmpty();
    }
    
    @Override
    public RelTypeElementIterator setSrc( RelIdArray newSrc )
    {
        return new FastRelTypeElement( getType(), getNode(), newSrc, direction, srcIterator.position() );
    }
}
