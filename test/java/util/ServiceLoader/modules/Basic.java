/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @library modules
 * @build bananascript/*
 * @compile src/pearscript/org/pear/PearScriptEngineFactory.java
 *          src/pearscript/org/pear/PearScript.java
 * @run testng/othervm Basic
 * @summary Basic test for ServiceLoader with a provider deployed as a module.
 */

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.ServiceLoader.Provider;
import java.util.stream.Collectors;
import javax.script.ScriptEngineFactory;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import static org.testng.Assert.*;

/**
 * Basic test for ServiceLoader. The test make use of two service providers:
 * 1. BananaScriptEngine - a ScriptEngineFactory deployed as a module on the
 *    module path.
 * 2. PearScriptEngine - a ScriptEngineFactory deployed on the class path
 *    with a service configuration file.
 */

public class Basic {

    // Copy the services configuration file for "pearscript" into place.
    @BeforeTest
    public void setup() throws Exception {
        Path src = Paths.get(System.getProperty("test.src", ""));
        Path classes = Paths.get(System.getProperty("test.classes", ""));
        String st = ScriptEngineFactory.class.getName();
        Path config = Paths.get("META-INF", "services", st);
        Path source = src.resolve("src").resolve("pearscript").resolve(config);
        Path target = classes.resolve(config);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Basic test of iterator() to ensure that providers located as modules
     * and on the class path are found.
     */
    @Test
    public void testIterator() {
        ServiceLoader<ScriptEngineFactory> loader
            = ServiceLoader.load(ScriptEngineFactory.class);
        Set<String> names = collectAll(loader)
                .stream()
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertTrue(names.contains("BananaScriptEngine"));
        assertTrue(names.contains("PearScriptEngine"));
    }

    /**
     * Basic test of iterator() to test iteration order. Providers deployed
     * as named modules should be found before providers deployed on the class
     * path.
     */
    @Test
    public void testIteratorOrder() {
        ServiceLoader<ScriptEngineFactory> loader
            = ServiceLoader.load(ScriptEngineFactory.class);
        boolean foundUnnamed = false;
        for (ScriptEngineFactory factory : collectAll(loader)) {
            if (factory.getClass().getModule().isNamed()) {
                if (foundUnnamed) {
                    assertTrue(false, "Named module element after unnamed");
                }
            } else {
                foundUnnamed = true;
            }
        }
    }

    /**
     * Basic test of Provider::type
     */
    @Test
    public void testProviderType() {
        Set<String> types = ServiceLoader.load(ScriptEngineFactory.class)
                .stream()
                .map(Provider::type)
                .map(Class::getName)
                .collect(Collectors.toSet());
        assertTrue(types.contains("org.banana.BananaScriptEngineFactory"));
        assertTrue(types.contains("org.pear.PearScriptEngineFactory"));
    }

    /**
     * Basic test of Provider::get
     */
    @Test
    public void testProviderGet() {
        Set<String> names = ServiceLoader.load(ScriptEngineFactory.class)
                .stream()
                .map(Provider::get)
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertTrue(names.contains("BananaScriptEngine"));
        assertTrue(names.contains("PearScriptEngine"));
    }

    /**
     * Basic test of stream() to ensure that elements for providers in named
     * modules come before elements for providers in unnamed modules.
     */
    @Test
    public void testStreamOrder() {
        List<Class<?>> types = ServiceLoader.load(ScriptEngineFactory.class)
                .stream()
                .map(Provider::type)
                .collect(Collectors.toList());

        boolean foundUnnamed = false;
        for (Class<?> factoryClass : types) {
            if (factoryClass.getModule().isNamed()) {
                if (foundUnnamed) {
                    assertTrue(false, "Named module element after unnamed");
                }
            } else {
                foundUnnamed = true;
            }
        }
    }

    /**
     * Basic test of ServiceLoader.findFirst()
     */
    @Test
    public void testFindFirst() {
        Optional<ScriptEngineFactory> ofactory
            = ServiceLoader.load(ScriptEngineFactory.class).findFirst();
        assertTrue(ofactory.isPresent());
        ScriptEngineFactory factory = ofactory.get();
        assertTrue(factory.getClass().getModule().isNamed());

        class S { }
        assertFalse(ServiceLoader.load(S.class).findFirst().isPresent());
    }

    /**
     * Basic test ServiceLoader.load specifying the platform class loader.
     * The providers on the module path and class path should not be located.
     */
    @Test
    public void testWithPlatformClassLoader() {
        ClassLoader pcl = ClassLoader.getPlatformClassLoader();

        // iterator
        ServiceLoader<ScriptEngineFactory> loader
                = ServiceLoader.load(ScriptEngineFactory.class, pcl);
        Set<String> names = collectAll(loader)
                .stream()
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("BananaScriptEngine"));
        assertFalse(names.contains("PearScriptEngine"));

        // stream
        names = ServiceLoader.load(ScriptEngineFactory.class, pcl)
                .stream()
                .map(Provider::get)
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("BananaScriptEngine"));
        assertFalse(names.contains("PearScriptEngine"));
    }

    /**
     * Basic test of ServiceLoader.load, using the class loader for
     * a module in a custom layer as the context.
     */
    @Test
    public void testWithCustomLayer1() {
        Layer layer = createCustomLayer("bananascript");

        ClassLoader loader = layer.findLoader("bananascript");
        List<ScriptEngineFactory> providers
            = collectAll(ServiceLoader.load(ScriptEngineFactory.class, loader));

        // should have at least 2 x bananascript + pearscript
        assertTrue(providers.size() >= 3);

        // first element should be the provider in the custom layer
        ScriptEngineFactory factory = providers.get(0);
        assertTrue(factory.getClass().getClassLoader() == loader);
        assertTrue(factory.getClass().getModule().getLayer() == layer);
        assertTrue(factory.getEngineName().equals("BananaScriptEngine"));

        // remainder should be the boot layer
        providers.remove(0);
        Set<String> names = providers.stream()
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertTrue(names.contains("BananaScriptEngine"));
        assertTrue(names.contains("PearScriptEngine"));
    }

    /**
     * Basic test of ServiceLoader.load, using a custom Layer as the context.
     */
    @Test
    public void testWithCustomLayer2() {
        Layer layer = createCustomLayer("bananascript");

        List <ScriptEngineFactory> providers
            = collectAll(ServiceLoader.load(layer, ScriptEngineFactory.class));

        // should have at least 2 x bananascript
        assertTrue(providers.size() >= 2);

        // first element should be the provider in the custom layer
        ScriptEngineFactory factory = providers.get(0);
        assertTrue(factory.getClass().getModule().getLayer() == layer);
        assertTrue(factory.getEngineName().equals("BananaScriptEngine"));

        // remainder should be the boot layer
        providers.remove(0);
        Set<String> names = providers.stream()
                .map(ScriptEngineFactory::getEngineName)
                .collect(Collectors.toSet());
        assertTrue(names.contains("BananaScriptEngine"));
        assertFalse(names.contains("PearScriptEngine"));
    }


    // -- nulls --

    @Test(expectedExceptions = { NullPointerException.class })
    public void testLoadNull1() {
        ServiceLoader.load(null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testLoadNull2() {
        ServiceLoader.load((Class<?>) null, ClassLoader.getSystemClassLoader());
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testLoadNull3() {
        class S { }
        ServiceLoader.load((Layer) null, S.class);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testLoadNull4() {
        ServiceLoader.load(Layer.empty(), null);
    }

    @Test(expectedExceptions = { NullPointerException.class })
    public void testLoadNull5() {
        ServiceLoader.loadInstalled(null);
    }

    /**
     * Create a custom Layer by resolving the given module names. The modules
     * are located in the {@code ${test.classes}/modules} directory.
     */
    private Layer createCustomLayer(String... modules) {
        Path dir = Paths.get(System.getProperty("test.classes", "."), "modules");
        ModuleFinder finder = ModuleFinder.of(dir);
        Set<String> roots = new HashSet<>();
        Collections.addAll(roots, modules);
        Layer bootLayer = Layer.boot();
        Configuration parent = bootLayer.configuration();
        Configuration cf = parent.resolveRequires(finder, ModuleFinder.of(), roots);
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Layer layer = bootLayer.defineModulesWithOneLoader(cf, scl);
        assertTrue(layer.modules().size() == 1);
        return layer;
    }

    private <E> List<E> collectAll(ServiceLoader<E> loader) {
        List<E> list = new ArrayList<>();
        Iterator<E> iterator = loader.iterator();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list;
    }
}

