package com.github.hotware.hsearch.event;

public interface EventSource {

	public void disable(boolean disable);

	public void setEventConsumer(EventConsumer eventConsumer);

}
