/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes.dbCommitted;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/8/12
 * Time: 6:01 PM
 */
public class DbSettings {
  // default; .idea/
  public static File getDbFilePath(final Project project) {
    final File vcs = new File(PathManager.getSystemPath(), "vcs");
    File file = new File(vcs, "historyCache");
    file.mkdirs();
    return new File(file, project.getLocationHash());
  }
}
