package com.nflabs.zeppelin.server;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import org.apache.wicket.protocol.http.WicketServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import com.nflabs.zeppelin.conf.ZeppelinConfiguration;
import com.nflabs.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import com.nflabs.zeppelin.rest.ZeppelinImpl;


public class ZeppelinServer extends Application {
	static Logger logger = Logger.getLogger(ZeppelinServer.class.getName());
	
	public static void main(String [] args) throws Exception{
		if(System.getProperty("log4j.configuration")==null){
			ConsoleAppender console = new ConsoleAppender(); // create appender
			// configure the appender
			String PATTERN = "%d [%p|%c|%C{1}] %m%n";
			console.setLayout(new PatternLayout(PATTERN));
			console.setThreshold(Level.DEBUG);
			console.activateOptions();
			// add appender to any Logger (here is root)
			Logger.getRootLogger().addAppender(console);
		}
		
		ZeppelinConfiguration conf = ZeppelinConfiguration.create();


		int port = conf.getInt(ConfVars.ZEPPELIN_PORT);
        int timeout = 1000*30;
        final Server server = new Server();
        SocketConnector connector = new SocketConnector();

        // Set some timeout options to make debugging easier.
        connector.setMaxIdleTime(timeout);
        connector.setSoLingerTime(-1);
        connector.setPort(port);
        server.addConnector(connector);
        
		final ServletHolder cxfServletHolder = new ServletHolder( new CXFNonSpringJaxrsServlet() );
		cxfServletHolder.setInitParameter("javax.ws.rs.Application", ZeppelinServer.class.getName());
		cxfServletHolder.setName("rest");
		cxfServletHolder.setForcedPath("rest");
		final ServletContextHandler cxfContext = new ServletContextHandler();
		cxfContext.setSessionHandler(new SessionHandler());
		cxfContext.setContextPath("/cxf");
		cxfContext.addServlet( cxfServletHolder, "/zeppelin/*" ); 

		
		// web
		WebAppContext sch = new WebAppContext();
        sch.setParentLoaderPriority(true);
        File resourcePath = new File(conf.getString(ConfVars.ZEPPELIN_WAR));
        
        if(resourcePath.isDirectory()){
        	System.setProperty("wicket.configuration", "development");
            ServletHolder wicketServletHolder = new ServletHolder(WicketServlet.class);

            wicketServletHolder.setInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM, "com.nflabs.zeppelin.web.WicketApplication");
            wicketServletHolder.setInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        	
            sch.setWar(resourcePath.getAbsolutePath());
            sch.addServlet(wicketServletHolder, "/");


        } else {
        	sch.setWar(resourcePath.getAbsolutePath());
        }
        
        
        // dynamic resource
        WebAppContext res = new WebAppContext();
        DefaultServlet sv = new DefaultServlet();

        ServletHolder resServletHolder = new ServletHolder(sv);
        resServletHolder.setName("resources");
        //resServletHolder.setInitParameter(param, value)
        res.setWar(conf.getString(ConfVars.ZEPPELIN_RESOURCE_DIR));
        res.addServlet(resServletHolder, "/resources/*");
        

	    ContextHandlerCollection contexts = new ContextHandlerCollection();
	    contexts.setHandlers(new Handler[]{cxfContext, sch, res});
	    server.setHandler(contexts);
	        
	        
	    logger.info("Start zeppelin server");
        server.start();
        logger.info("Started");
        
		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
            	logger.info("Shutting down Zeppelin Server ... ");
            	try {
					server.stop();
				} catch (Exception e) {
					logger.error("Error while stopping servlet container", e);
				}
            	
            	logger.info("Bye");
            }
        });

        while (true)
        {
            Thread.sleep(10000);
        }
	}
	
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(ZeppelinImpl.class);
 
        return classes;
    }
}
