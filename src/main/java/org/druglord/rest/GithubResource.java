/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.druglord.rest;

import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 *
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@javax.ws.rs.Path("/github")
public class GithubResource
{
   // private static final String CLIENT_ID = "6305dace2dca7699e5b6";
   // private static final String CLIENT_SECRET = "068f1e5ccdb287b1439bc6efe817e65f33b35ede";
   private static final String CLIENT_ID = "c8fa300d687e84f68109";
   private static final String CLIENT_SECRET = "eb96f86ca79c69209ffbc3e23dcf7696f333d171";

   @GET
   @javax.ws.rs.Path("/fork")
   public Response init(@QueryParam("repo") String repo) throws Exception
   {
      StringBuilder url = new StringBuilder();
      url.append("https://github.com/login/oauth/authorize");
      url.append("?scope=user:email,public_repo");
      url.append("&state=" + URLEncoder.encode(repo, "UTF-8"));
      url.append("&client_id=").append(CLIENT_ID);
      return Response.temporaryRedirect(URI.create(url.toString())).build();
   }

   @GET
   @javax.ws.rs.Path("/callback")
   public Response callback(@QueryParam("code") String code, @QueryParam("state") String repository,
            @Context UriInfo uriInfo)
   {
      String uri = "http://github.com";
      Client client = ClientBuilder.newClient();
      try
      {
         Map<String, String> tokenMap = postToken(code, repository, client);
         String accessToken = tokenMap.get("access_token");
         JsonObject response = forkRepository(accessToken, repository, client);
         // createRepository(accessToken, repository, client);
         uri = response.getString("html_url");
      }
      finally
      {
         client.close();
      }

      return Response.temporaryRedirect(URI.create(uri)).build();
   }

   JsonObject forkRepository(String accessToken, String repository, Client client)
   {
      Response response = client
               .target("https://api.github.com/repos/" + repository + "/forks")
               .request()
               .accept(MediaType.APPLICATION_JSON_TYPE)
               .header("Authorization", "token " + accessToken)
               .header("User-Agent", "Github Forker")
               .post(Entity.text(""));
      int status = response.getStatus();
      if (status == 202)
      {
         return response.readEntity(JsonObject.class);
      }
      else
      {
         String msg = response.readEntity(String.class);
         throw new WebApplicationException(msg, status);
      }

   }

   private Map<String, String> postToken(String code, String state, Client client)
   {
      Map<String, String> responseMap = new HashMap<>();
      MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
      map.putSingle("client_id", CLIENT_ID);
      map.putSingle("client_secret", CLIENT_SECRET);
      map.putSingle("code", code);
      map.putSingle("state", state);
      // access_token=e72e16c7e42f292c6912e7710c838347ae178b4a&scope=user%2Cgist&token_type=bearer
      String responseStr = client
               .target("https://github.com/login/oauth/access_token")
               .request()
               .post(Entity.form(map), String.class);
      for (String entry : responseStr.split("&"))
      {
         String[] pair = entry.split("=");
         responseMap.put(pair[0], pair[1]);
      }
      return responseMap;
   }
}
