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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.event.manage.config.TenantMappingProperties;
import java.util.Collections;
import org.junit.Test;

public class AccountQualifierServiceTest {

  @Test
  public void convertFromTenant_defaultNoMapping() {
    final TenantMappingProperties properties = new TenantMappingProperties();

    final AccountQualifierService service = new AccountQualifierService(properties);

    assertThat(
        service.convertFromTenant("123456"),
        equalTo("UNKNOWN:123456")
    );
    assertThat(
        service.convertFromTenant("any:123456"),
        equalTo("UNKNOWN:123456")
    );

  }

  @Test
  public void convertFromTenant_noTenantPrefix() {
    final TenantMappingProperties properties = new TenantMappingProperties()
        .setTenantPrefixToAccountTypes(Collections.singletonMap("hybrid", "CORE"))
        .setDefaultAccountType("UNKNOWN");

    final AccountQualifierService service = new AccountQualifierService(properties);

    assertThat(
        service.convertFromTenant("123456"),
        equalTo("UNKNOWN:123456")
    );

  }

  @Test
  public void convertFromTenant_tenantPrefixKnown() {
    final TenantMappingProperties properties = new TenantMappingProperties()
        .setTenantPrefixToAccountTypes(Collections.singletonMap("hybrid", "CORE"))
        .setDefaultAccountType("UNKNOWN");

    final AccountQualifierService service = new AccountQualifierService(properties);

    assertThat(
        service.convertFromTenant("hybrid:123456"),
        equalTo("CORE:123456")
    );

  }

  @Test
  public void convertFromTenant_tenantPrefixUnknown() {
    final TenantMappingProperties properties = new TenantMappingProperties()
        .setTenantPrefixToAccountTypes(Collections.singletonMap("hybrid", "CORE"))
        .setDefaultAccountType("UNKNOWN");

    final AccountQualifierService service = new AccountQualifierService(properties);

    assertThat(
        service.convertFromTenant("other:123456"),
        equalTo("UNKNOWN:123456")
    );

  }

}