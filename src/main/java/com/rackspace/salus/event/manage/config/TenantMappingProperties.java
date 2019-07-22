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

package com.rackspace.salus.event.manage.config;

import java.util.Map;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("salus.tenant-mapping")
@Component
@Data
public class TenantMappingProperties {

  /**
   * When configured, tenants will be processed into qualified accounts during Kapacitor setup
   * by mapping a tenant type prefix given as a key here into a qualified account with the mapped
   * account type value.
   */
  Map<String,String> tenantPrefixToAccountTypes;

  /**
   * When no prefix is found on a given tenant, this is the account type prefix that will be
   * pre-pended to the tenant value separated by the
   */
  @NotEmpty
  String defaultAccountType = "RCN";

  /**
   * The delimiter to expect when processing tenant values.
   */
  @NotEmpty
  String tenantDelimiter = ":";

  /**
   * The delimiter to use when constructing a qualified account value.
   */
  @NotEmpty
  String qualifiedAccountDelimiter = ":";
}
