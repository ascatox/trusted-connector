/*-
 * ========================LICENSE_START=================================
 * ids-api
 * %%
 * Copyright (C) 2019 Fraunhofer AISEC
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package de.fhg.aisec.ids.api.settings;

import java.io.Serializable;

public final class ConnectorConfig implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String appstoreUrl;
  private final String brokerUrl;
  private final String ttpHost;
  private final int ttpPort;
  private final String acmeServerWebcon;
  private final String acmeDnsWebcon;
  private final int acmePortWebcon;
  private final boolean tosAcceptWebcon;
  private final String dapsUrl;
  private final String keystoreName;
  private final String keystorePassword;
  private final String keystoreAliasName;
  private final String truststoreName;
  private final String connectorUUID;

  public ConnectorConfig() {
    appstoreUrl =
        "https://raw.githubusercontent.com/industrial-data-space/templates/master/templates.json";
    brokerUrl = "";
    ttpHost = "";
    ttpPort = 443;
    acmeServerWebcon = "";
    acmeDnsWebcon = "";
    acmePortWebcon = 80;
    tosAcceptWebcon = false;
    dapsUrl = "https://daps.aisec.fraunhofer.de";
    keystoreName = "client-keystore.jks";
    keystorePassword = "password";
    keystoreAliasName = "1";
    truststoreName = "client-truststore.jks";
    connectorUUID = "00c6155a-966d-4d2c-af6e-60f997e24da2";
  }

  public String getAppstoreUrl() {
    return appstoreUrl;
  }

  public String getBrokerUrl() {
    return brokerUrl;
  }

  public String getTtpHost() {
    return ttpHost;
  }

  public int getTtpPort() {
    return ttpPort;
  }

  public String getAcmeServerWebcon() {
    return acmeServerWebcon;
  }

  public String getAcmeDnsWebcon() {
    return acmeDnsWebcon;
  }

  public int getAcmePortWebcon() {
    return acmePortWebcon;
  }

  public boolean isTosAcceptWebcon() {
    return tosAcceptWebcon;
  }

  public String getDapsUrl() {
    return dapsUrl;
  }

  public String getKeystoreName() {
    return keystoreName;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getKeystoreAliasName() {
    return keystoreAliasName;
  }

  public String getTruststoreName() {
    return truststoreName;
  }

  public String getConnectorUUID() {
    return connectorUUID;
  }
}
