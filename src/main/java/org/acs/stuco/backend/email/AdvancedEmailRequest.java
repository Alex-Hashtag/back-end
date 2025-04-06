package org.acs.stuco.backend.email;


import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Setter
@Getter
public class AdvancedEmailRequest
{

    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String body;
    private boolean html;
    private List<Attachment> attachments;

    public AdvancedEmailRequest()
    {
    }

    public AdvancedEmailRequest(List<String> to, List<String> cc, List<String> bcc, String subject, String body, boolean html, List<Attachment> attachments)
    {
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
        this.body = body;
        this.html = html;
        this.attachments = attachments;
    }


}