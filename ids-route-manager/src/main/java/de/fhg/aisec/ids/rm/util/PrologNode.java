/*-
 * ========================LICENSE_START=================================
 * ids-route-manager
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
package de.fhg.aisec.ids.rm.util;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

import java.util.ArrayList;
import java.util.List;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.WhenDefinition;
import org.checkerframework.checker.nullness.qual.NonNull;

/** Represents a node in the EIP diagram tree */
public class PrologNode {
  //	public String id;
  public String nodeType;
  public String value;
  //	public String predicate = "has_url";
  public List<ProcessorDefinition<?>> outputs;

  public PrologNode(@NonNull String id, @NonNull Object node) {
    //		this.id = id;

    if (node instanceof ProcessorDefinition) {
      ProcessorDefinition<?> processorType = (ProcessorDefinition<?>) node;
      //			this.predicate = "has_operation";
      this.value = processorType.getLabel();
    }
    if (node instanceof FromDefinition) {
      FromDefinition fromType = (FromDefinition) node;
      this.nodeType = "from";
      //			this.predicate = "has_url";
      this.value = fromType.getEndpointUri();
    } else if (node instanceof ToDefinition) {
      ToDefinition toType = (ToDefinition) node;
      this.value = toType.getEndpointUri();
      this.nodeType = "to";
    } else if (node instanceof FilterDefinition) {
      this.nodeType = "message_filter";
    } else if (node instanceof WhenDefinition) {
      this.nodeType = "when";
      this.value = ((WhenDefinition) node).getExpression().getExpression();
    } else if (node instanceof OtherwiseDefinition) {
      this.nodeType = "otherwise";
      this.value = "";
    } else if (node instanceof ChoiceDefinition) {
      ChoiceDefinition choice = (ChoiceDefinition) node;
      List<ProcessorDefinition<?>> outputs = new ArrayList<>(choice.getWhenClauses());
      if (choice.getOtherwise() != null) {
        outputs.add(choice.getOtherwise());
      }
      this.outputs = outputs;
      this.nodeType = "choice";
    } else if (node instanceof RecipientListDefinition) {
      //			this.predicate = "recipient_list";
      this.value = ((RecipientListDefinition) node).getLabel();
      this.nodeType = "recipients";
    } else if (node instanceof RoutingSlipDefinition) {
      this.value = ((RoutingSlipDefinition) node).getLabel();
      this.nodeType = "slip";
    } else if (node instanceof SplitDefinition) {
      this.nodeType = "splitter";
    } else if (node instanceof AggregateDefinition) {
      this.nodeType = "aggregator";
    } else if (node instanceof ResequenceDefinition) {
      //			this.predicate = "resequence";
      this.value = ((ResequenceDefinition) node).getLabel();
    } else if (node instanceof BeanDefinition) {
      //			this.predicate = "bean";
      this.value = ((BeanDefinition) node).getLabel();
    } else if (node instanceof TransformDefinition) {
      this.value = ((TransformDefinition) node).getLabel();
      //			this.predicate = "transform";
    }

    // lets auto-default as many values as we can
    if (isEmpty(this.nodeType) && node != null) {
      String name = node.getClass().getName();
      int idx = name.lastIndexOf('.');
      if (idx > 0) {
        name = name.substring(idx + 1);
      }
      if (name.endsWith("Type")) {
        name = name.substring(0, name.length() - 4);
      }
      this.nodeType = name;
    }
    if (isEmpty(this.value) && isNotEmpty(this.nodeType)) {
      this.value = this.nodeType;
    }
    if (node instanceof ProcessorDefinition && this.outputs == null) {
      ProcessorDefinition<?> processorType = (ProcessorDefinition<?>) node;
      this.outputs = processorType.getOutputs();
    }
  }
}
