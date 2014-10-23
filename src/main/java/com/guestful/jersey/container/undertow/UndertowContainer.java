/**
 * Copyright (C) 2013 Guestful (info@guestful.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.guestful.jersey.container.undertow;

import com.guestful.jersey.container.Container;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.ws.rs.core.Application;
import java.util.*;

/**
 * date 2014-05-28
 *
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 */
public class UndertowContainer extends Container {

    private Undertow server;

    @Override
    protected void doStart() throws Exception {
        server = buildServer();
        server.start();
    }

    @Override
    protected void doStop() throws Exception {
        server.stop();
        server = null;
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    @Override
    public boolean isStopped() {
        return server == null;
    }

    private Undertow buildServer() throws ServletException {
        String ctxPath = getContextPath();
        if (!ctxPath.startsWith("/")) {
            ctxPath = "/" + ctxPath;
        }

        DeploymentInfo webapp = Servlets.deployment()
            .addServletContainerInitalizers(getServletContainerInitializers())
            .setClassLoader(Thread.currentThread().getContextClassLoader())
            .setContextPath(ctxPath)
            .setDefaultEncoding("UTF-8")
            .setDeploymentName(UndertowContainer.class.getSimpleName())
            .setDisplayName(UndertowContainer.class.getSimpleName())
            .setEagerFilterInit(true);

        Class<? extends Application> app = getApplicationClass();
        if (app != null) {
            ServletInfo holder = Servlets.servlet(app.getName(), ServletContainer.class)
                .setLoadOnStartup(0)
                .setAsyncSupported(true)
                .setEnabled(true)
                .addMapping("/*")
                .addInitParam(ServletProperties.JAXRS_APPLICATION_CLASS, app.getName());
            webapp.addServlet(holder);
        }

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(webapp);
        manager.deploy();

        PathHandler path = Handlers.path(Handlers.redirect(ctxPath)).addPrefixPath(ctxPath, manager.start());

        Undertow.Builder builder = Undertow.builder()
            .addHttpListener(getPort(), "0.0.0.0")
            .setHandler(path);

        if (getMaxWorkers() > 0) {
            builder.setWorkerThreads(getMaxWorkers());
        }

        return builder.build();
    }

    private List<ServletContainerInitializerInfo> getServletContainerInitializers() {
        List<ServletContainerInitializerInfo> infos = new ArrayList<>();
        for (ServletContainerInitializer initializer : ServiceLoader.load(ServletContainerInitializer.class)) {
            HandlesTypes types = initializer.getClass().getAnnotation(HandlesTypes.class);
            infos.add(new ServletContainerInitializerInfo(
                initializer.getClass(),
                new ImmediateInstanceFactory<>(initializer),
                types == null ? Collections.emptySet() : new LinkedHashSet<>(Arrays.asList(types.value()))));
        }
        return infos;
    }

}
