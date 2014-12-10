package com.github.hotware.hsearch.event;

import java.util.Properties;

public interface EventSourceProvider {
	
	public EventSource getEventSource(Properties properties);

}
