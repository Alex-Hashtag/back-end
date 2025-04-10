package org.acs.stuco.backend.auth.event;

import org.acs.stuco.backend.user.User;


public record UserVerifiedEvent(User user)
{
}

