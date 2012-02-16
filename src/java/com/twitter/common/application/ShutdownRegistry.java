// =================================================================================================
// Copyright 2011 Twitter, Inc.
// -------------------------------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this work except in compliance with the License.
// You may obtain a copy of the License in the LICENSE file, or at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =================================================================================================

package com.twitter.common.application;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import com.twitter.common.base.Command;
import com.twitter.common.base.ExceptionalCommand;

/**
 * A shutdown action controller. It executes actions in the reverse order they were registered, and
 * logs a warning for every shutdown action that fails, but doesn't prevent completion of subsequent
 * actions or the normal completion of the {@code execute()} method.
 *
 * @author Attila Szegedi
 */
public interface ShutdownRegistry {

  <E extends Exception, T extends ExceptionalCommand<E>> void addAction(T action);

  public static class ShutdownRegistryImpl implements ShutdownRegistry, Command {
    private static final Logger LOG = Logger.getLogger(ShutdownRegistry.class.getName());

    private final List<ExceptionalCommand<? extends Exception>> actions = Lists.newLinkedList();

    private boolean completed = false;

    /**
     * Registers an action to execute during {@link #execute()}. It is an error to call this method
     * after calling {@link #execute()}.
     *
     * @param action the action to add to the list of actions to execute during execution
     */
    @Override
    public synchronized <E extends Exception, T extends ExceptionalCommand<E>> void addAction(
        T action) {
      Preconditions.checkState(!completed);
      actions.add(action);
    }

    /**
     * Executes an application shutdown stage by executing all registered actions. This method can
     * be called multiple times but will only execute the registered actions the first time.
     *
     * This sends output to System.out because logging is unreliable during JVM shutdown, which
     * this class may be used for.
     */
    @Override
    public synchronized void execute() {
      if (!completed) {
        LOG.info(String.format("Executing %d shutdown commands.", actions.size()));
        completed = true;
        try {
          for (ExceptionalCommand<? extends Exception> action : Lists.reverse(actions)) {
            try {
              action.execute();
            } catch (Exception e) {
              LOG.log(Level.WARNING, "Shutdown action failed.", e);
            }
          }
        } finally {
          actions.clear();
        }
      } else {
        LOG.info("Action controller has already completed, subsequent calls ignored.");
      }
    }
  }
}
