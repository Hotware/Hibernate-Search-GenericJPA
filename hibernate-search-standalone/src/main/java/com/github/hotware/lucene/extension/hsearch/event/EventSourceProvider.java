package com.github.hotware.lucene.extension.hsearch.event;

import java.util.Properties;

public interface EventSourceProvider {
	
	public EventSource getEventSource(Properties properties);

}
