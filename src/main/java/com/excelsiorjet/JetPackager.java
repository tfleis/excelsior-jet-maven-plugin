/*
 * Copyright (c) 2015, Excelsior LLC.
 *
 *  This file is part of Excelsior JET Maven Plugin.
 *
 *  Excelsior JET Maven Plugin is free software:
 *  you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Excelsior JET Maven Plugin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Excelsior JET Maven Plugin.
 *  If not, see <http://www.gnu.org/licenses/>.
 *
*/
package com.excelsiorjet;

/**
 * Excelsior JET "xpack" tool executor utility class.
 *
 * @author Nikita Lipsky
 */
public class JetPackager extends JetTool {

    static final String JET_PACKAGER = "xpack";

    public JetPackager(JetHome jetHome, String... args) {
        super(jetHome, JET_PACKAGER, args);
    }

    public JetPackager(String... args) throws JetHomeException {
        super(JET_PACKAGER, args);
    }
}
