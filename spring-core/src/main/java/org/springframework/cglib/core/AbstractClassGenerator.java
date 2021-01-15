/*
 * Copyright 2003,2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cglib.core;

import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.springframework.asm.ClassReader;
import org.springframework.cglib.core.internal.Function;
import org.springframework.cglib.core.internal.LoadingCache;

/**
 * Abstract class for all code-generating CGLIB utilities.
 * In addition to caching generated classes for performance, it provides hooks for
 * customizing the <code>ClassLoader</code>, name of the generated class, and transformations
 * applied before generation.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
abstract public class AbstractClassGenerator<T> implements ClassGenerator {

	private static final ThreadLocal CURRENT = new ThreadLocal();

	private static volatile Map<ClassLoader, ClassLoaderData> CACHE = new WeakHashMap<ClassLoader, ClassLoaderData>();

	private static final boolean DEFAULT_USE_CACHE =
			Boolean.parseBoolean(System.getProperty("cglib.useCache", "true"));


	private GeneratorStrategy strategy = DefaultGeneratorStrategy.INSTANCE;

	private NamingPolicy namingPolicy = DefaultNamingPolicy.INSTANCE;

	private Source source;

	private ClassLoader classLoader;

	private Class contextClass;

	private String namePrefix;

	private Object key;

	private boolean useCache = DEFAULT_USE_CACHE;

	private String className;

	private boolean attemptLoad;


	protected static class ClassLoaderData {

		private final Set<String> reservedClassNames = new HashSet<String>();

		/**
		 * {@link AbstractClassGenerator} here holds "cache key" (e.g. {@link org.springframework.cglib.proxy.Enhancer}
		 * configuration), and the value is the generated class plus some additional values
		 * (see {@link #unwrapCachedValue(Object)}.
		 * <p>The generated classes can be reused as long as their classloader is reachable.</p>
		 * <p>Note: the only way to access a class is to find it through generatedClasses cache, thus
		 * the key should not expire as long as the class itself is alive (its classloader is alive).</p>
		 */
		private final LoadingCache<AbstractClassGenerator, Object, Object> generatedClasses;

		/**
		 * Note: ClassLoaderData object is stored as a value of {@code WeakHashMap<ClassLoader, ...>} thus
		 * this classLoader reference should be weak otherwise it would make classLoader strongly reachable
		 * and alive forever.
		 * Reference queue is not required since the cleanup is handled by {@link WeakHashMap}.
		 */
		private final WeakReference<ClassLoader> classLoader;

		private final Predicate uniqueNamePredicate = new Predicate() {
			public boolean evaluate(Object name) {
				return reservedClassNames.contains(name);
			}
		};

		private static final Function<AbstractClassGenerator, Object> GET_KEY = new Function<AbstractClassGenerator, Object>() {
			public Object apply(AbstractClassGenerator gen) { /* 函数式接口放入 ClassLoaderData */
				return gen.key;
			}
		};

		public ClassLoaderData(ClassLoader classLoader) {
			if (classLoader == null) { /* 类加载器不能为空 */
				throw new IllegalArgumentException("classLoader == null is not yet supported");
			}
			this.classLoader = new WeakReference<ClassLoader>(classLoader); /* 设置类加载器为弱引用，弱引用在下次垃圾回收的时候就会回收 */
			Function<AbstractClassGenerator, Object> load = /* FutureTask中调用，生成具体的字节码 */
					new Function<AbstractClassGenerator, Object>() {
						public Object apply(AbstractClassGenerator gen) {
							Class klass = gen.generate(ClassLoaderData.this);
							return gen.wrapCachedClass(klass);
						}
					};
			generatedClasses = new LoadingCache<AbstractClassGenerator, Object, Object>(GET_KEY, load);
		}

		public ClassLoader getClassLoader() {
			return classLoader.get();
		}

		public void reserveName(String name) {
			reservedClassNames.add(name);
		}

		public Predicate getUniqueNamePredicate() {
			return uniqueNamePredicate;
		}

		public Object get(AbstractClassGenerator gen, boolean useCache) {
			if (!useCache) {
				return gen.generate(ClassLoaderData.this);
			}
			else {
				/*
				* 进入LoadingCache.get方法，在跳入的方法中，直接跳入，ClassLoaderData 的 gen.key【GET_KEY】获取到 要创建那个类的 名字（第一次是 EnhancerKey）
				* 然后获取 这个 key 的对象
				* 如果获取不到，则创建一个对象，在创建对象的时候，会有一个 FutureTask 的创建，
				* FutureTask 调用 call方法， call方法最后调用 ClassLoaderData 中的第二个函数式接口 【load】，load 去创建类
				*
				* 最终 【【【第一次得到的这个  cachedValue 就是 EnhancerKey的代理实现类】】】
				* */
				Object cachedValue = generatedClasses.get(gen);
				return gen.unwrapCachedValue(cachedValue);
			}
		}
	}


	protected T wrapCachedClass(Class klass) {
		return (T) new WeakReference(klass);
	}

	protected Object unwrapCachedValue(T cached) {
		return ((WeakReference) cached).get();
	}


	protected static class Source {

		String name;

		public Source(String name) {
			this.name = name;
		}
	}


	protected AbstractClassGenerator(Source source) {
		this.source = source;
	}

	protected void setNamePrefix(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	final protected String getClassName() {
		return className;
	}

	private void setClassName(String className) {
		this.className = className;
	}

	private String generateClassName(Predicate nameTestPredicate) {
		return namingPolicy.getClassName(namePrefix, source.name, key, nameTestPredicate);
	}

	/**
	 * Set the <code>ClassLoader</code> in which the class will be generated.
	 * Concrete subclasses of <code>AbstractClassGenerator</code> (such as <code>Enhancer</code>)
	 * will try to choose an appropriate default if this is unset.
	 * <p>
	 * Classes are cached per-<code>ClassLoader</code> using a <code>WeakHashMap</code>, to allow
	 * the generated classes to be removed when the associated loader is garbage collected.
	 * @param classLoader the loader to generate the new class with, or null to use the default
	 */
	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	// SPRING PATCH BEGIN
	public void setContextClass(Class contextClass) {
		this.contextClass = contextClass;
	}
	// SPRING PATCH END

	/**
	 * Override the default naming policy.
	 * @param namingPolicy the custom policy, or null to use the default
	 * @see DefaultNamingPolicy
	 */
	public void setNamingPolicy(NamingPolicy namingPolicy) {
		if (namingPolicy == null)
			namingPolicy = DefaultNamingPolicy.INSTANCE;
		this.namingPolicy = namingPolicy;
	}

	/**
	 * @see #setNamingPolicy
	 */
	public NamingPolicy getNamingPolicy() {
		return namingPolicy;
	}

	/**
	 * Whether use and update the static cache of generated classes
	 * for a class with the same properties. Default is <code>true</code>.
	 */
	public void setUseCache(boolean useCache) {
		this.useCache = useCache;
	}

	/**
	 * @see #setUseCache
	 */
	public boolean getUseCache() {
		return useCache;
	}

	/**
	 * If set, CGLIB will attempt to load classes from the specified
	 * <code>ClassLoader</code> before generating them. Because generated
	 * class names are not guaranteed to be unique, the default is <code>false</code>.
	 */
	public void setAttemptLoad(boolean attemptLoad) {
		this.attemptLoad = attemptLoad;
	}

	public boolean getAttemptLoad() {
		return attemptLoad;
	}

	/**
	 * Set the strategy to use to create the bytecode from this generator.
	 * By default an instance of {@link DefaultGeneratorStrategy} is used.
	 */
	public void setStrategy(GeneratorStrategy strategy) {
		if (strategy == null)
			strategy = DefaultGeneratorStrategy.INSTANCE;
		this.strategy = strategy;
	}

	/**
	 * @see #setStrategy
	 */
	public GeneratorStrategy getStrategy() {
		return strategy;
	}

	/**
	 * Used internally by CGLIB. Returns the <code>AbstractClassGenerator</code>
	 * that is being used to generate a class in the current thread.
	 */
	public static AbstractClassGenerator getCurrent() {
		return (AbstractClassGenerator) CURRENT.get();
	}

	public ClassLoader getClassLoader() {
		ClassLoader t = classLoader;
		if (t == null) {
			t = getDefaultClassLoader();
		}
		if (t == null) {
			t = getClass().getClassLoader();
		}
		if (t == null) {
			t = Thread.currentThread().getContextClassLoader();
		}
		if (t == null) {
			throw new IllegalStateException("Cannot determine classloader");
		}
		return t;
	}

	abstract protected ClassLoader getDefaultClassLoader();

	/**
	 * Returns the protection domain to use when defining the class.
	 * <p>
	 * Default implementation returns <code>null</code> for using a default protection domain. Sub-classes may
	 * override to use a more specific protection domain.
	 * </p>
	 * @return the protection domain (<code>null</code> for using a default)
	 */
	protected ProtectionDomain getProtectionDomain() {
		return null;
	}

	protected Object create(Object key) {
		try {
			ClassLoader loader = getClassLoader(); /* APP类加载器 */
			/*
			* 当前类加载器对应的缓存，缓存的key是类加载器，缓存的value是 classLoaderData
			* ClassLoaderData可以理解成一个“包含具体业务逻辑的处理过程”，是两个回调函数（虽然看着不是lambda表达式，但是作用是一样的）一个是返回 gen.key【GET_KEY】，一个为了创建具体的class【load】
			* */
			Map<ClassLoader, ClassLoaderData> cache = CACHE; /* ClassLoaderData 这个东西挺重要的！ */
			/*
			* 第一次获取的时候是空的，但是第二次获取的时候就不空了，因为第一次创建的 EnhancerKey的时候已经往里添加了值，不会重复创建
			* */
			ClassLoaderData data = cache.get(loader); /* 先从缓存中获取当前类加载器所有加载过的类 */
			if (data == null) {
				synchronized (AbstractClassGenerator.class) {
					cache = CACHE;
					data = cache.get(loader);
					if (data == null) {
						Map<ClassLoader, ClassLoaderData> newCache = new WeakHashMap<ClassLoader, ClassLoaderData>(cache); /* 新建一个缓存Cache， 并将之前的缓存Cache的数据放进来，并将已经被gc回收的数据清除掉 */
						/*
						* 新建一个当前类加载器对应的classLoaderData并加载到缓存中，
						* ClassLoaderData 包含两个回调函数（虽然看着不是lambda表达式，但是作用是一样的）一个是返回 gen.key【GET_KEY】，一个为了创建具体的class【load】
						* */
						data = new ClassLoaderData(loader);
						newCache.put(loader, data);
						CACHE = newCache; /* 刷新全局数据 */
					}
				}
			}
			this.key = key;
			Object obj = data.get(this, getUseCache()); /* 传入代理生成器，并根据代理类生成器获得返回值 */
			if (obj instanceof Class) {
				/*
				* =======================================
				* =======================================
				* 执行 newInstance方法 返回一个代理类的对象，
				* 第一次返回的 EnhancerKey 的代理实现类的对象
				* =======================================
				* =======================================
				*  */
				return firstInstance((Class) obj);
			}
			return nextInstance(obj);
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CodeGenerationException(ex);
		}
	}

	protected Class generate(ClassLoaderData data) {
		Class gen;
		Object save = CURRENT.get(); /* 当前代理类生成器放入 ThreadLocal */
		CURRENT.set(this);
		try {
			ClassLoader classLoader = data.getClassLoader();
			if (classLoader == null) {
				throw new IllegalStateException("ClassLoader is null while trying to define class " +
						getClassName() + ". It seems that the loader has been expired from a weak reference somehow. " +
						"Please file an issue at cglib's issue tracker.");
			}
			synchronized (classLoader) {
				String name = generateClassName(data.getUniqueNamePredicate()); /* 代理类名生成 */
				data.reserveName(name); /* 存入缓存 */
				this.setClassName(name);
			}
			if (attemptLoad) {
				try {
					gen = classLoader.loadClass(getClassName());
					return gen;
				}
				catch (ClassNotFoundException e) {
					// ignore
				}
			}
			byte[] b = strategy.generate(this);  /* 生成字节码 */
			String className = ClassNameReader.getClassName(new ClassReader(b));
			ProtectionDomain protectionDomain = getProtectionDomain();
			synchronized (classLoader) { // just in case
				// SPRING PATCH BEGIN
				gen = ReflectUtils.defineClass(className, b, classLoader, protectionDomain, contextClass);
				// SPRING PATCH END
			}
			return gen;
		}
		catch (RuntimeException | Error ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new CodeGenerationException(ex);
		}
		finally {
			CURRENT.set(save);
		}
	}

	abstract protected Object firstInstance(Class type) throws Exception;

	abstract protected Object nextInstance(Object instance) throws Exception;

}
