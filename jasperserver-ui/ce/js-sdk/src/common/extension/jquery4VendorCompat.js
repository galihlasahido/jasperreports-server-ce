/*
 * Copyright (C) 2005 - 2022 TIBCO Software Inc. All rights reserved.
 * http://www.jaspersoft.com.
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Memulihkan API jQuery yang dihapus di versi 4, khusus untuk plugin pihak
 * ketiga yang dibekukan dan tidak bisa diperbarui.
 *
 * Lima paket yang dibundel ke aplikasi ini berasal dari git.jaspersoft.com,
 * server internal Jaspersoft yang sudah tidak ada (DNS-nya pun tidak
 * teresolusi). Mereka bertahan hanya sebagai tarball di yarn-offline-mirror,
 * jadi tidak ada versi yang lebih baru untuk diambil:
 *
 *   jqueryui-timepicker-addon   $.trim, $.isFunction, $.isWindow
 *   jquery-ui-touch-punch       $.proxy
 *   jquery.selection            $.trim, $.isArray, $.isFunction, $.isNumeric,
 *                               $.parseJSON, $.now, $.type, $.camelCase,
 *                               $.proxy, $.isWindow
 *   jquery.urldecoder           $.isArray
 *   jCryption                   $.isFunction
 *
 * Dua pembantu tes juga memerlukannya: jquery-simulate ($.camelCase) dan
 * jasmine-jquery ($.trim).
 *
 * PENTING: berkas ini BUKAN izin memakai API tersebut di kode kita sendiri.
 * Seluruh pemanggilan di src/ sudah diganti dengan padanan bahasa saat naik
 * ke jQuery 4. Berkas ini murni utang teknis yang terikat pada kelima fork
 * di atas, dan bisa dihapus begitu semuanya diganti atau di-fork ulang dari
 * asal publiknya.
 *
 * Implementasi di bawah menyalin perilaku jQuery 3 supaya plugin-plugin itu
 * berjalan persis seperti sebelumnya.
 */
import $ from 'jquery';

const class2type = {};
const toString = class2type.toString;
const hasOwn = class2type.hasOwnProperty;

'Boolean Number String Function Array Date RegExp Object Error Symbol'
    .split(' ')
    .forEach(function (name) {
        class2type['[object ' + name + ']'] = name.toLowerCase();
    });

function jqType(obj) {
    if (obj == null) {
        return obj + '';
    }
    return typeof obj === 'object' || typeof obj === 'function'
        ? class2type[toString.call(obj)] || 'object'
        : typeof obj;
}

if (typeof $.type !== 'function') {
    $.type = jqType;
}

if (typeof $.trim !== 'function') {
    $.trim = function (text) {
        return text == null ? '' : (text + '').trim();
    };
}

if (typeof $.isArray !== 'function') {
    $.isArray = Array.isArray;
}

if (typeof $.isFunction !== 'function') {
    // jQuery 3.3+ mengecualikan elemen DOM: di beberapa browser lawas
    // <object> melaporkan dirinya sebagai function.
    $.isFunction = function (obj) {
        return typeof obj === 'function' && typeof obj.nodeType !== 'number';
    };
}

if (typeof $.isWindow !== 'function') {
    $.isWindow = function (obj) {
        return obj != null && obj === obj.window;
    };
}

if (typeof $.isNumeric !== 'function') {
    $.isNumeric = function (obj) {
        const t = jqType(obj);
        return (t === 'number' || t === 'string') && !isNaN(obj - parseFloat(obj));
    };
}

if (typeof $.parseJSON !== 'function') {
    $.parseJSON = function (data) {
        return JSON.parse(data + '');
    };
}

if (typeof $.now !== 'function') {
    $.now = Date.now;
}

if (typeof $.camelCase !== 'function') {
    // -ms- diperlakukan khusus: satu-satunya prefiks vendor yang huruf
    // depannya kecil.
    $.camelCase = function (string) {
        return string
            .replace(/^-ms-/, 'ms-')
            .replace(/-([a-z])/g, function (all, letter) {
                return letter.toUpperCase();
            });
    };
}

if (typeof $.proxy !== 'function') {
    $.guid = $.guid || 1;
    $.proxy = function (fn, context) {
        if (typeof context === 'string') {
            const tmp = fn[context];
            context = fn;
            fn = tmp;
        }
        if (typeof fn !== 'function') {
            return undefined;
        }
        const args = Array.prototype.slice.call(arguments, 2);
        const proxy = function () {
            return fn.apply(context || this, args.concat(Array.prototype.slice.call(arguments)));
        };
        proxy.guid = fn.guid = fn.guid || $.guid++;
        return proxy;
    };
}

export default $;
