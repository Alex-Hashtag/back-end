package org.acs.stuco.backend.email;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class Attachment
{

    private String fileName;
    private String contentType;
    private String contentBase64;

    public Attachment()
    {
    }

    public Attachment(String fileName, String contentType, String contentBase64)
    {
        this.fileName = fileName;
        this.contentType = contentType;
        this.contentBase64 = contentBase64;
    }


}

