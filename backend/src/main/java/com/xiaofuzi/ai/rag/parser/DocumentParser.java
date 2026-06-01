package com.xiaofuzi.ai.rag.parser;

import org.springframework.ai.document.Document;

import java.io.InputStream;
import java.util.List;

public interface DocumentParser {

    boolean supports(String fileName);

    List<Document> parse(InputStream inputStream) throws Exception;

    default int getPriority() {
        return 0;
    }
}
