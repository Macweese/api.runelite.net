/*
 * Copyright (c) 2019, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.http.service.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.runelite.http.api.config.ConfigEntry;
import net.runelite.http.api.config.ConfigPatch;
import net.runelite.http.api.config.Configuration;
import net.runelite.http.service.account.AuthFilter;
import net.runelite.http.service.account.beans.SessionEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/config")
public class ConfigController
{
	private final ConfigService configService;
	private final AuthFilter authFilter;

	@Autowired
	public ConfigController(ConfigService configService, AuthFilter authFilter)
	{
		this.configService = configService;
		this.authFilter = authFilter;
	}

	@GetMapping
	public Configuration get(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);

		if (session == null)
		{
			return null;
		}

		Map<String, String> userConfig = configService.get(session.getUser());

		List<ConfigEntry> config = new ArrayList<>(userConfig.size());
		for (Map.Entry<String, String> entry : userConfig.entrySet())
		{
			ConfigEntry c = new ConfigEntry();
			c.setKey(entry.getKey());
			c.setValue(entry.getValue());
			config.add(c);
		}

		return new Configuration(config);
	}

	@GetMapping("/v2")
	public Map<String, String> get2(HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);

		if (session == null)
		{
			return null;
		}

		return configService.get(session.getUser());
	}

	@PatchMapping
	public List<String> patch(
		HttpServletRequest request,
		HttpServletResponse response,
		@RequestBody Configuration changes
	) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);
		if (session == null)
		{
			return null;
		}

		ConfigPatch patch = new ConfigPatch();
		for (ConfigEntry entry : changes.getConfig())
		{
			if (entry.getValue() == null)
			{
				patch.getUnset().add(entry.getKey());
			}
			else
			{
				patch.getEdit().put(entry.getKey(), entry.getValue());
			}
		}

		List<String> failures = configService.patch(session.getUser(), patch);
		if (failures.size() != 0)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return failures;
		}

		return null;
	}

	@PatchMapping("/v2")
	public List<String> patch(
		HttpServletRequest request,
		HttpServletResponse response,
		@RequestBody ConfigPatch patch
	) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);
		if (session == null)
		{
			return null;
		}

		List<String> failures = configService.patch(session.getUser(), patch);
		if (failures.size() != 0)
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return failures;
		}

		return null;
	}

	@RequestMapping(path = "/{key:.+}", method = PUT)
	public void setKey(
		HttpServletRequest request,
		HttpServletResponse response,
		@PathVariable String key,
		@RequestBody(required = false) String value
	) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);

		if (session == null)
		{
			return;
		}

		if (!configService.setKey(session.getUser(), key, value))
		{
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(path = "/{key:.+}", method = DELETE)
	public void unsetKey(
		HttpServletRequest request,
		HttpServletResponse response,
		@PathVariable String key
	) throws IOException
	{
		SessionEntry session = authFilter.handle(request, response);

		if (session == null)
		{
			return;
		}

		if (!configService.unsetKey(session.getUser(), key))
		{
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
