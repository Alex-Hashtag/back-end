package org.acs.stuco.backend.news.event;

import org.acs.stuco.backend.news.NewsPost;


public record NewsPostCreatedEvent(NewsPost newsPost)
{
}
