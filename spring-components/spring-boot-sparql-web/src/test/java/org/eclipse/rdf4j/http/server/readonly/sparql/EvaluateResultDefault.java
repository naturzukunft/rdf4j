package org.eclipse.rdf4j.http.server.readonly.sparql;

import java.io.OutputStream;

public class EvaluateResultDefault implements EvaluateResult
{
    private String contentType;

    private OutputStream outputstream;

    public EvaluateResultDefault(OutputStream outputstream) {
        this.outputstream = outputstream;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public OutputStream getOutputstream() {
        return outputstream;
    }

    public void setOutputstream(OutputStream outputstream) {
        this.outputstream = outputstream;
    }
}
