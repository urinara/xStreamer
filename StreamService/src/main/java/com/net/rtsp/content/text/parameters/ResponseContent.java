package com.net.rtsp.content.text.parameters;

public class ResponseContent extends com.net.rtsp.content.ResponseContent {

	protected ResponseContent(byte[] content ) {
		super(content, ContentHandler.CONTENT_TYPE);
	}
}
