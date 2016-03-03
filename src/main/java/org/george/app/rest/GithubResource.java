/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.george.app.rest;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.util.Base64;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 *
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
@Path("/github")
@ApplicationScoped
public class GithubResource
{

   // Create a Third-party application in Github
   // TODO: Move this to a parameterized thing
   private static final String CLIENT_ID = "cf62f13b1faca9bb9bdf";
   private static final String CLIENT_SECRET = "d57da5f8b50cee449aa6903dfa306402e24641de";

   @GET
   @Path("/fork")
   public Response forkRepositoryInit(@QueryParam("repo") String repo) throws Exception
   {
      JsonObject state = Json.createObjectBuilder().add("repository", repo).add("action", "fork").build();
      StringBuilder url = new StringBuilder();
      url.append("https://github.com/login/oauth/authorize");
      url.append("?scope=user:email,public_repo");
      url.append("&state=" + Base64.getEncoder().encodeToString(state.toString().getBytes()));
      url.append("&client_id=").append(CLIENT_ID);
      return Response.temporaryRedirect(URI.create(url.toString())).build();
   }

   @GET
   @Path("/new")
   public Response newRepositoryInit(@QueryParam("repo") String repo) throws Exception
   {
      JsonObject state = Json.createObjectBuilder().add("repository", repo).add("action", "new").build();
      StringBuilder url = new StringBuilder();
      url.append("https://github.com/login/oauth/authorize");
      url.append("?scope=user:email,public_repo");
      url.append("&state=" + Base64.getEncoder().encodeToString(state.toString().getBytes()));
      url.append("&client_id=").append(CLIENT_ID);
      return Response.temporaryRedirect(URI.create(url.toString())).build();
   }

   @GET
   @Path("/callback")
   public Response callback(@QueryParam("code") String code, @QueryParam("state") String state)
   {
      String uri = "http://github.com";
      Client client = ClientBuilder.newClient();
      try
      {
         String json = new String(Base64.getDecoder().decode(state.getBytes()));
         JsonObject obj = Json.createReader(new StringReader(json)).readObject();
         String repository = obj.getString("repository");
         JsonObject tokenObj = postToken(code, repository, client);
         String accessToken = tokenObj.getString("access_token");
         String action = obj.getString("action");
         if ("new".equals(action))
         {
            createRepository(accessToken, repository, client);
         }
         else if ("fork".equals(action))
         {
            JsonObject response = forkRepository(accessToken, repository, client);
            uri = response.getString("html_url");
         }
      }
      finally
      {
         client.close();
      }

      return Response.temporaryRedirect(URI.create(uri)).build();
   }

   private JsonObject postToken(String code, String state, Client client)
   {
      MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
      map.putSingle("client_id", CLIENT_ID);
      map.putSingle("client_secret", CLIENT_SECRET);
      map.putSingle("code", code);
      map.putSingle("state", state);
      // {"access_token":"3d4bf6b3ea93fba1dbfeeb5fa5afb5226e0cbec9","token_type":"bearer","scope":"public_repo,user:email"}
      JsonObject obj = client
               .target("https://github.com/login/oauth/access_token")
               .request()
               .accept(MediaType.APPLICATION_JSON_TYPE)
               .post(Entity.form(map), JsonObject.class);
      return obj;
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

   void createRepository(String accessToken, String repository, Client client)
   {
      JsonObjectBuilder json = Json.createObjectBuilder();
      json.add("name", repository)
               .add("description", "Created via Forge Online")
               .add("homepage", "http://forge.jboss.org")
               .add("private", false)
               .add("has_issues", true)
               .add("has_wiki", true)
               .add("has_downloads", true);
      Response response = client
               .target("https://api.github.com/user/repos")
               .request()
               .accept(MediaType.APPLICATION_JSON_TYPE)
               .header("Authorization", "token " + accessToken)
               .header("User-Agent", "Forge Online App")
               .post(Entity.json(json.build()));
      int status = response.getStatus();
      if (status != 201)
      {
         String msg = response.readEntity(String.class);
         throw new WebApplicationException(msg, status);
      }
   }

   void push(String repo, String username, String accessToken) throws Exception
   {
      java.nio.file.Path tmpDir = Files.createTempDirectory("tmpdir");
      try (Git git = Git.cloneRepository().setDirectory(tmpDir.toFile()).setURI("https://github.com/" + repo).call())
      {
         git.add().addFilepattern(".").call();
         git.commit().setAll(true).setMessage("Initial Commit").call();
         Iterable<PushResult> result = git.push()
                  .setRemote("https://github.com/" + repo + ".git")
                  .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, accessToken))
                  .setPushAll().call();
         for (PushResult pushResult : result)
         {
            System.out.println(pushResult.getMessages());
         }
      }
   }
}
