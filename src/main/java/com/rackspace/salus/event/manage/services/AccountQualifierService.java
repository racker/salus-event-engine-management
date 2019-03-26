/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.event.manage.services;

import com.rackspace.salus.event.manage.config.TenantMappingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class AccountQualifierService {

  private final TenantMappingProperties properties;

  @Autowired
  public AccountQualifierService(TenantMappingProperties properties) {
    this.properties = properties;
  }

  public String convertFromTenant(String tenantId) {

    if (properties.getTenantToAccountTypes() == null) {
      log.debug("Skipping tenant type mapping since configuration is absent");
      return tenantId;
    }

    final String[] tenantParts = tenantId.split(properties.getTenantDelimiter(), 2);

    if (tenantParts.length == 1) {
      Assert.state(
          properties.getDefaultAccountType() != null,
          "defaultAccountType needs to be configured in TenantMappingProperties"
      );

      log.debug("No prefix on tenant, mapping qualified account with default type");
      return properties.getDefaultAccountType() +
          properties.getQualifiedAccountDelimiter() +
          tenantId;
    }

    final String tenantPrefix = tenantParts[0];

    final String accountType = properties.getTenantToAccountTypes().get(tenantPrefix);
    if (StringUtils.hasText(accountType)) {
      log.debug("Found accountType={} for tenant prefix={}", accountType, tenantPrefix);
      return accountType +
          properties.getQualifiedAccountDelimiter() +
          tenantParts[1];
    } else {
      log.warn("No account type mapping found for tenant prefix={}", tenantPrefix);
      return properties.getDefaultAccountType() +
          properties.getQualifiedAccountDelimiter() +
          tenantParts[1];
    }
  }
}
