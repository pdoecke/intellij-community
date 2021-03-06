// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsDifferenceSender;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class ToolWindowUsagesCollector extends ApplicationUsagesCollector implements FUStatisticsDifferenceSender {

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages() {
    ToolWindowCollector.State state = ToolWindowCollector.getInstance().getState();
    assert state != null;
    return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(UsageDescriptorKeyValidator.ensureProperKey(e.getKey()), e.getValue()));
  }

  @Override
  @NotNull
  public String getGroupId() {
    return "statistics.toolwindows.performed";
  }

  @Override
  public FUSUsageContext getContext() {
    return FUSUsageContext.DEFAULT;
  }
}

