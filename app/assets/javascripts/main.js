/*
 * Copyright (C) 2016 Language Technology Group and Interactive Graphics Systems Group, Technische Universität Darmstadt, Germany
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

// `main.js` is the file that sbt-web will use as an entry point
(function (requirejs) {
    'use strict';

    requirejs.config({
        packages: ['libs'],
        baseUrl: './assets/javascripts',
        paths: {
            'jsRoutes': ['jsroutes'],
            'angular': 'libs/angular/angular',
            'jquery': 'libs/jquery/dist/jquery.min',
            'jquery-json': 'libs/jquery-json/dist/jquery.json.min',
            'awesome-slider': 'libs/angular-awesome-slider/dist/angular-awesome-slider.min',
            'ngAnimate': 'libs/angular-animate/angular-animate',
            'ngAria': 'libs/angular-aria/angular-aria',
            'ngMessages': 'libs/angular-messages/angular-messages.min',
            'ngMaterial': 'libs/angular-material/angular-material.min',
            'bootstrap': 'libs/bootstrap/dist/js/bootstrap.min',
            'ui-bootstrap': 'libs/angular-bootstrap/ui-bootstrap-tpls.min',
            'toggle-switch': 'libs/angular-toggle-switch/angular-toggle-switch.min',
            'ngSanitize': 'libs/angular-sanitize/angular-sanitize.min',
            'scDateTime': 'libs/sc-date-time/dist/sc-date-time',
            'moment': 'libs/moment/min/moment.min',
            'ui-layout': 'libs/angular-ui-layout/src/ui-layout',
            'ui-router': 'libs/angular-ui-router/release/angular-ui-router.min',
            'angularMoment': 'libs/angular-moment/angular-moment.min',
            'screenfull': 'libs/screenfull/dist/screenfull',
            'angularScreenfull': 'libs/angular-screenfull/dist/angular-screenfull.min',
            'highcharts': 'libs/highcharts-release/highcharts',
            'ngFileSaver': 'libs/angular-file-saver/dist/angular-file-saver.bundle.min',
            'drilldown' : 'libs/highcharts-release/modules/drilldown',
            'underscore': 'libs/underscore/underscore-min',
            'd3': 'libs/d3/d3',
            'angularResizable': 'libs/angular-resizable/angular-resizable.min',
            'bootstrapFileField': 'libs/angular-bootstrap-file-field/angular-bootstrap-file-field.min'
        },
        shim: {
            'jsRoutes': {
                exports: 'jsRoutes'
            },
            'jquery': {
                exports: 'JQuery'
            },
            'ui-layout': {
                exports: 'angular',
                deps: ['angular', 'ngAnimate']
            },
            'angularMoment': {
                deps: ['angular', 'moment']
            },
            'angularScreenfull': {
                deps: ['angular', 'screenfull']
            },
            'angular': {
                exports: 'angular'
            },
            'awesome-slider': {
                exports: 'angular',
                deps: ['angular']
            },
            'ngAnimate': {
                exports: 'angular',
                deps: ['angular']
            },
            'ngAria': {
                exports: 'angular',
                deps: ['angular']
            },
            'bootstrap': {
                deps: ['jquery']
            },
            'ui-bootstrap': {
                deps: ['angular', 'bootstrap', 'ngAnimate']
            },
            'toggle-switch': {
                exports: 'angular',
                deps: ['angular']
            },
            'ngSanitize': {
                exports: 'angular',
                deps: ['angular']
            },
            'ui-router': {
                exports: 'angular',
                deps: ['angular']
            },
            'scDateTime': {
                deps: ['angular']
            },
            'highcharts': {
                exports: 'Highcharts',
                deps: ['jquery']
            },
            'ngMaterial': {
                deps: ['angular','ngAria','ngMessages','ngAnimate']
            },
            'drilldown': {
                exports: 'drilldown',
                deps: ['highcharts']
            },
            'underscore': {
                exports: '_'
            },
            'jquery.mousewheel' :{
                exports: 'jquery.mousewheel',
                deps: ['jquery']
            },
            'd3': {
                exports: 'd3'
            },
            'angularResizable' : {
                exports: 'angularResizable',
                deps: ['angular']
            },
            'bootstrapFileField' : {
                deps: ['angular', 'bootstrap']
            },
            'ngFileSaver': {
                deps: ['angular']
            }
        },
        priority: [
            'jquery',
            'angular',
        ],
        deps: ['angular','jquery'],
        waitSeconds: 5

    });

    requirejs.onError = function (err) {
        console.log(err);
    };

    require([
            'angular',
            'app'
        ], function(angular, app) {
            angular.bootstrap(document, ['myApp']);
        }
    );


})(requirejs);
