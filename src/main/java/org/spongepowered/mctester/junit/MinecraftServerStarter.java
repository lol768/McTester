/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.mctester.junit;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.mctester.internal.RealJUnitRunner;

/**
 * Helper to programmatically manage a Minecraft server.
 *
 * @author Michael Vorburger
 */
public class MinecraftServerStarter {

	private LaunchClassLoader minecraftServerClassLoader;

	private static MinecraftServerStarter INSTANCE = new MinecraftServerStarter();
	private boolean started;

	public static MinecraftServerStarter INSTANCE() {
		return INSTANCE;
	}

	private MinecraftServerStarter() { }

	/**
	 * Starts Minecraft Server.
	 *
	 * This launches a background thread.
	 *
	 * @throws Throwable
	 */
	public void startServer() throws Throwable {
	    if (this.started) {
	        return;
        }

	    this.started = true;
        String existing = System.getProperty("fml.coreMods.load");
        if (existing == null) {
            existing = "";
        }

        System.setProperty("fml.coreMods.load", existing + "," + "org.spongepowered.mod.SpongeCoremod");

		String[] args = new String[] { "--tweakClass", "org.spongepowered.mctester.junit.MinecraftRunnerTweaker", "--gameDir", RealJUnitRunner.GLOBAL_SETTINGS.getGameDir().getAbsolutePath()};
		// TODO instead ch.vorburger.minecraft.testsinfra.GradleStartTestServer.getTweakClass()
		//new GradleStartTestServer().launch(args);
		Class clazz = Class.forName("GradleStart");
		Thread wrapperThread = new Thread(() -> {
			try {
				clazz.getMethod("main", String[].class).invoke(null, (Object) args);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Minecraft.getMinecraft().shutdown();
			}
		});

		wrapperThread.start();

	}

	public ClassLoader getMinecraftServerClassLoader() {
	    RunnerEvents.waitForLaunchClassLoaderFuture();
		return internalGetMinecraftServerClassLoader();
	}

	private ClassLoader internalGetMinecraftServerClassLoader() {
		if (minecraftServerClassLoader == null) {
			minecraftServerClassLoader = Launch.classLoader;
		}
		return minecraftServerClassLoader;
	}

}
