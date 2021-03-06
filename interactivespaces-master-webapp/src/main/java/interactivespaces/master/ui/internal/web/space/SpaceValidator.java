/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package interactivespaces.master.ui.internal.web.space;

import interactivespaces.master.ui.internal.web.FormObjectValidator;

import org.springframework.validation.Errors;

/**
 * A validator for {@link SpaceForm} instances.
 *
 * @author Keith M. Hughes
 */
public class SpaceValidator extends FormObjectValidator {

  /**
   * Validate a space form.
   *
   * @param spaceForm
   *          the space form
   * @param errors
   *          the errors
   */
 public void validate(SpaceForm spaceForm, Errors errors) {
    String name = spaceForm.getSpace().getName();
    if (!hasValue(name)) {
      errors.rejectValue("space.name", "required", "required");
    }
  }
}
