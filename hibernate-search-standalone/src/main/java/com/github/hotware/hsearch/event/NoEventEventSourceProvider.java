package com.github.hotware.hsearch.event;

import java.util.Properties;

public class NoEventEventSourceProvider implements EventSourceProvider {
	
	private static final NoEventEventSource INSTANCE = new NoEventEventSource();
	
	@Override
	public EventSource getEventSource(Properties properties) {
		return INSTANCE;
	}

}
