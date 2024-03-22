/*******************************************************************************
 * Copyright (c) 2022 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.collection.factory.mapdb;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.eclipse.rdf4j.collection.factory.api.BindingSetKey;
import org.eclipse.rdf4j.collection.factory.api.CollectionFactory;
import org.eclipse.rdf4j.collection.factory.impl.DefaultCollectionFactory;
import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MutableBindingSet;
import org.mapdb.DB;
import org.mapdb.DB.HashMapMaker;
import org.mapdb.DBException;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerJava;

public class MapDb3CollectionFactory implements CollectionFactory {
	// The size 16 seems like a nice starting value but others could well
	// be better.
	private static final int SWITCH_TO_DISK_BASED_SET_AT_SIZE = 16;
	protected volatile DB db;
	protected volatile long colectionId = 0;
	protected final long iterationCacheSyncThreshold;
	private final CollectionFactory delegate;
	// The chances that someone would run a 32bit non "sun" vm are just miniscule
	// So I am not going to worry about this.
	private static final boolean ON_32_BIT_VM = "32".equals(System.getProperty("sun.arch.data.model"));

	protected static final class RDF4jMapDB3Exception extends RDF4JException {

		private static final long serialVersionUID = 1L;

		public RDF4jMapDB3Exception(String string, Exception e) {
			super(string, e);
		}

	}

	public MapDb3CollectionFactory(long iterationCacheSyncThreshold) {
		this(iterationCacheSyncThreshold, new DefaultCollectionFactory());
	}

	public MapDb3CollectionFactory(long iterationCacheSyncThreshold, CollectionFactory delegate) {

		this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
		this.delegate = delegate;
	}

	protected void init() {
		if (this.db == null) {
			synchronized (this) {
				if (this.db == null) {
					try {
						final Maker dbmaker = DBMaker.tempFileDB().closeOnJvmShutdown();
						// On 32 bit machines this may fail to often so guard it.
						if (!ON_32_BIT_VM) {
							// mmap is much faster than random access file.
							dbmaker.fileMmapEnable();
						}
						this.db = dbmaker.make();
					} catch (DBException e) {
						throw new RDF4jMapDB3Exception("could not initialize temp db", e);
					}
				}
			}
		}
	}

	@Override
	public <T> List<T> createList() {
		return delegate.createList();
	}

	@Override
	public List<Value> createValueList() {
		return delegate.createValueList();
	}

	@Override
	public Set<BindingSet> createSetOfBindingSets(Supplier<MutableBindingSet> create,
			Function<String, Predicate<BindingSet>> getHas, Function<String, Function<BindingSet, Value>> getget,
			Function<String, BiConsumer<Value, MutableBindingSet>> getSet) {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<BindingSet> serializer = createBindingSetSerializer(create, getHas, getget, getSet);
			MemoryTillSizeXSet<BindingSet> set = new MemoryTillSizeXSet<>(colectionId++,
					delegate.createSetOfBindingSets(), serializer);
			return new CommitingSet<>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createSetOfBindingSets();
		}
	}

	@Override
	public <T> Set<T> createSet() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<T> serializer = createAnySerializer();
			MemoryTillSizeXSet<T> set = new MemoryTillSizeXSet<T>(colectionId++, delegate.createSet(), serializer);
			return new CommitingSet<T>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createSet();
		}
	}

	@Override
	public Set<Value> createValueSet() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<Value> serializer = createValueSerializer();
			Set<Value> set = new MemoryTillSizeXSet<>(colectionId++, delegate.createValueSet(), serializer);
			return new CommitingSet<Value>(set, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createValueSet();
		}
	}

	@Override
	public <K, V> Map<K, V> createMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<K> keySerializer = createAnySerializer();
			Serializer<V> valueSerializer = createAnySerializer();
			HashMapMaker<K, V> hashMap = db.hashMap(Long.toHexString(colectionId++), keySerializer, valueSerializer);
			HTreeMap<K, V> create = hashMap.create();
			return new CommitingMap<>(create, iterationCacheSyncThreshold, db);
		} else {
			return delegate.createMap();
		}
	}

	@Override
	public <V> Map<Value, V> createValueKeyedMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<Value> keySerializer = createValueSerializer();
			Serializer<V> valueSerializer = createAnySerializer();
			return new CommitingMap<>(
					db.hashMap(Long.toHexString(colectionId++), keySerializer, valueSerializer).create(),
					iterationCacheSyncThreshold, db);
		} else {
			return delegate.createValueKeyedMap();
		}
	}

	@Override
	public <T> Queue<T> createQueue() {
		return delegate.createQueue();
	}

	@Override
	public Queue<Value> createValueQueue() {
		return delegate.createValueQueue();
	}

	@Override
	public void close() throws RDF4JException {
		if (db != null) {
			db.close();
		}
	}

	@Override
	public <E> Map<BindingSetKey, E> createGroupByMap() {
		if (iterationCacheSyncThreshold > 0) {
			init();
			Serializer<BindingSetKey> keySerializer = createBindingSetKeySerializer();
			Serializer<E> valueSerializer = createAnySerializer();
			return new CommitingMap<>(
					db.hashMap(Long.toHexString(colectionId++), keySerializer, valueSerializer).create(),
					iterationCacheSyncThreshold, db);
		} else {
			return delegate.createGroupByMap();
		}
	}

	@Override
	public final BindingSetKey createBindingSetKey(BindingSet bindingSet, List<Function<BindingSet, Value>> getValues,
			ToIntFunction<BindingSet> hashOfBindingSetCalculator) {
		List<Value> values = new ArrayList<>(getValues.size());
		for (int i = 0; i < getValues.size(); i++) {
			values.add(getValues.get(i).apply(bindingSet));
		}
		return new MapDb3BindingSetKey(values, hashOfBindingSetCalculator.applyAsInt(bindingSet));
	}

	protected static final class CommitingSet<T> extends AbstractSet<T> {
		private final Set<T> wrapped;
		private final long iterationCacheSyncThreshold;
		private final DB db;
		private long iterationCount;

		public CommitingSet(Set<T> wrapped, long iterationCacheSyncThreshold, DB db) {
			super();
			this.wrapped = wrapped;
			this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
			this.db = db;
		}

		@Override
		public boolean add(T e) {

			boolean res = wrapped.add(e);
			if (iterationCount++ % iterationCacheSyncThreshold == 0) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
			}
			return res;

		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			boolean res = wrapped.addAll(c);
			if (iterationCount + c.size() % iterationCacheSyncThreshold == 0) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
			}
			return res;
		}

		@Override
		public Iterator<T> iterator() {
			return wrapped.iterator();
		}

		@Override
		public int size() {
			return wrapped.size();
		}
	}

	protected static final class CommitingMap<K, V> extends AbstractMap<K, V> {
		private final Map<K, V> wrapped;
		private final long iterationCacheSyncThreshold;
		private final DB db;
		private long iterationCount;

		public CommitingMap(Map<K, V> wrapped, long iterationCacheSyncThreshold, DB db) {
			super();
			this.wrapped = wrapped;
			this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;
			this.db = db;
		}

		@Override
		public V put(K k, V v) {

			V res = wrapped.put(k, v);
			if (iterationCount++ % iterationCacheSyncThreshold == 0) {
				// write to disk every $iterationCacheSyncThreshold items
				db.commit();
			}
			return res;

		}

		@Override
		public int size() {
			return wrapped.size();
		}

		@Override
		public Set<Entry<K, V>> entrySet() {
			return wrapped.entrySet();
		}
	}

	/**
	 * Only create a disk based set once the contents are large enough that it starts to pay off.
	 *
	 * @param <T> of the contents of the set.
	 */
	protected class MemoryTillSizeXSet<V> extends AbstractSet<V> {
		private Set<V> wrapped;
		private final long setName;
		private final Serializer<V> valueSerializer;

		public MemoryTillSizeXSet(long setName, Set<V> wrapped, Serializer<V> valueSerializer) {
			super();
			this.setName = setName;
			this.wrapped = wrapped;
			this.valueSerializer = valueSerializer;
		}

		@Override
		public boolean add(V e) {
			if (wrapped instanceof HashSet && wrapped.size() > SWITCH_TO_DISK_BASED_SET_AT_SIZE) {
				Set<V> disk = db.hashSet(Long.toHexString(setName), valueSerializer).create();
				disk.addAll(wrapped);
				wrapped = disk;
			}
			return wrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends V> arg0) {
			if (wrapped instanceof HashSet && arg0.size() > SWITCH_TO_DISK_BASED_SET_AT_SIZE) {
				Set<V> disk = db.hashSet(Long.toHexString(setName), valueSerializer).create();
				disk.addAll(wrapped);
				wrapped = disk;
			}
			return wrapped.addAll(arg0);
		}

		@Override
		public void clear() {
			wrapped.clear();
		}

		@Override
		public boolean contains(Object o) {
			return wrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> arg0) {
			return wrapped.containsAll(arg0);
		}

		@Override
		public boolean isEmpty() {
			return wrapped.isEmpty();
		}

		@Override
		public boolean remove(Object o) {
			return wrapped.remove(o);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return wrapped.retainAll(c);
		}

		@Override
		public Object[] toArray() {
			return wrapped.toArray();
		}

		@Override
		public <T> T[] toArray(T[] arg0) {
			return wrapped.toArray(arg0);
		}

		@Override
		public Iterator<V> iterator() {
			return wrapped.iterator();
		}

		@Override
		public int size() {
			return wrapped.size();
		}

	}

	/**
	 * These methods should be overriding in case a store can deliver a better serialization protocol.
	 *
	 * @param getGet
	 * @param getHas
	 * @param create
	 */
	protected Serializer<BindingSet> createBindingSetSerializer(Supplier<MutableBindingSet> create,
			Function<String, Predicate<BindingSet>> getHas, Function<String, Function<BindingSet, Value>> getGet,
			Function<String, BiConsumer<Value, MutableBindingSet>> getSet) {
		return new BindingSetSerializer(createValueSerializer(), create, getHas, getGet, getSet);
	}

	protected <T> Serializer<T> createAnySerializer() {
		return new SerializerJava();
	}

	protected Serializer<Value> createValueSerializer() {
		return new ValueSerializer();
	}

	protected final Serializer<BindingSetKey> createBindingSetKeySerializer() {
		return new BindingSetKeySerializer(createValueSerializer());
	}
}