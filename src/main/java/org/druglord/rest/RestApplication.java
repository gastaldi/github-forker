package org.druglord.rest;

import java.util.Collections;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/api")
public class RestApplication extends Application
{
   @Override
   public Set<Object> getSingletons()
   {
      return Collections.singleton(new GithubResource());
   }

}