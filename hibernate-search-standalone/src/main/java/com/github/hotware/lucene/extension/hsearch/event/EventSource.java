package com.github.hotware.lucene.extension.hsearch.event;

public interface EventSource {

	public void disable(boolean disable);

	public void setEventConsumer(EventConsumer eventConsumer);

}
