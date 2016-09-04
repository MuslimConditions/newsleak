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

define([
    'angular',
    'highcharts',
    'drilldown',
    'bootstrap'
], function (angular) {
    'use strict';

    angular.module('myApp.histogram', ['play.routing'])
        .factory('HistogramFactory', [
            function() {
                var config = {
                    // Highcharts options
                    highchartsOptions: {
                        lang: {
                            drillUpText: 'Back to {series.name}',
                            loading: 'Loading...'
                        }
                    },
                    // General configuration for the chart
                    chartConfig: {
                        options: {
                            title: {
                                text: null
                            },
                            chart: {
                                type: 'column',
                                // height: 200,
                                backgroundColor: 'rgba(0,0,0,0)',
                                zoomType: 'xy',
                                resetZoomButton: {
                                    position: {
                                        align: 'left',
                                        y: -5
                                    }
                                },
                                reflow: true,
                                events: ""
                            },
                            credits: false,
                            xAxis: {
                                title: {
                                    text: 'Date',
                                    enabled: false
                                },
                                type: 'category'
                            },
                            yAxis: {
                                title: {
                                    text: 'Number of Documents<br>(logarithmic)',
                                    offset: 60
                                }
                                ,
                                type: 'logarithmic',
                                tickInterval: 2,
                                stackLabels: {
                                    enabled: true
                                },
                                gridLineWidth: 0
                            },
                            plotOptions: {
                                column: {
                                    grouping: false,
                                    shadow: false,
                                    dataLabels: {
                                        enabled: true,
                                        padding: 0
                                    }
                                }

                            },
                            legend: {
                                enabled: false

                            }
                        },
                        title: {
                            text: ''
                        },
                        drilldown: {
                            series: []
                        },
                        loading: false
                        //chart logic

                    }
                };
                return config;
            }
        ])
        /******************************** CONTROLLER ************************************/
        .controller('HistogramController', [
            '$scope',
            '$compile',
            '$timeout',
            '$q',
            'playRoutes',
            'HistogramFactory',
            'ObserverService',
            function ($scope, $compile, $timeout, $q, playRoutes, HistogramFactory, ObserverService) {

                $scope.initialized = false;
                $scope.drilldown = false;
                $scope.drillup = false;
                $scope.factory = HistogramFactory;
                $scope.chartConfig = angular.copy(HistogramFactory.chartConfig.options);
                $scope.observer = ObserverService;

                $scope.data = [];
                $scope.dataFilter = [];
                //current Level of Detail in Histogram
                $scope.currentLoD = "";
                $scope.currentRange = "";

                $scope.emptyFacets = [{'key':'dummy','data': []}];

                // fetch levels of detail from the backend
                $scope.observer.getHistogramLod().then(function(lod) {
                    $scope.lod  = angular.copy(lod);
                    $scope.currentLoD = $scope.lod[0];
                    $scope.updateHistogram();
                });


                /**
                 * subscribe entity and metadata filters
                 */
                $scope.observer_subscribe_entity = function(items) { $scope.entityFilters = items};
                $scope.observer_subscribe_metadata = function(items) { $scope.metadataFilters = items};
                $scope.observer_subscribe_fulltext = function(items) { $scope.fulltextFilters = items};
                $scope.observer.subscribeItems($scope.observer_subscribe_entity,"entity");
                $scope.observer.subscribeItems($scope.observer_subscribe_metadata,"metadata");
                $scope.observer.subscribeItems($scope.observer_subscribe_fulltext,"fulltext");

                /**
                 * Add time range filter to observer
                 *
                 * @param range - The range delivers the information for which time frame data
                 * shall be loaded (e. g. can be values like '1970-1979', '1970', 'Jan 1980').
                 */
                $scope.addTimeFilter = function(range) {
                    $scope.observer.addItem({
                        type: 'time',
                        data: {
                            name: range,
                            lod: $scope.currentLoD
                        }
                    });
                };

                // set language related options
                Highcharts.setOptions($scope.factory.highchartsOptions);

                $scope.clickedItem = function (category) {
                    $scope.addTimeFilter(category.name);
                };


                $scope.initHistogram = function() {
                    $scope.chartConfig["series"] = [{
                        name: 'Overview',
                        data: $scope.data,
                        cursor: 'pointer',
                        point: {
                            events: {
                                click: function(e) {
                                    $scope.clickedItem(this);
                                }
                            }
                        }
                    },{
                        name:  'Overview',
                        data: $scope.dataFilter,
                        color: 'black',
                        cursor: 'pointer',
                        point: {
                            events: {
                                click: function(e) {
                                    $scope.clickedItem(this);
                                }
                            }
                        },
                        dataLabels: {
                            inside: true,
                            verticalAlign: "top",
                            useHTML: true,
                            formatter : function() {
                                return $('<div/>').css({
                                    'color' : 'white'
                                }).text(this.y)[0].outerHTML;
                            }
                        }
                    }];

                    $scope.chartConfig.chart.events = {
                        drilldown: function(e) {
                            $scope.drillDown(e, this)
                        },
                        drillup: function(e) {
                            $scope.drillUp(e)
                        }
                    };
                    $scope.chartConfig.chart.renderTo = "histogram";

                    $scope.histogram = new Highcharts.Chart($scope.chartConfig);
                    $scope.initialized = true;
                };

                /**
                 * updated on filter changes
                 */
                $scope.updateHistogram = function() {
                    if($scope.histogram)
                        $scope.histogram.showLoading('Loading ...');
                    console.log("reload histogram");
                    var deferred = $q.defer();
                    var entities = [];
                    angular.forEach($scope.entityFilters, function(item) {
                        entities.push(item.data.id);
                    });
                    var facets = [];
                    if($scope.metadataFilters.length > 0) {
                        angular.forEach($scope.metadataFilters, function(metaType) {
                            if($scope.metadataFilters[metaType].length > 0) {
                                var keys = [];
                                angular.forEach($scope.metadataFilters[metaType], function(x) {
                                    keys.push(x.data.name);
                                });
                                facets.push({key: metaType, data: keys});
                            }
                        });
                        if(facets == 0) facets = $scope.emptyFacets;

                    } else {
                        facets = $scope.emptyFacets;
                    }
                    var fulltext = [];
                    angular.forEach($scope.fulltextFilters, function(item) {
                        fulltext.push(item.data.name);
                    });
                    //TODO: figure out: time filter vs. time range for histogram
                    //playRoutes.controllers.HistogramController.getHistogram(fulltext,facets,entities,$scope.observer.getTimeRange(),$scope.currentLoD).get().then(function(respone) {
                    playRoutes.controllers.HistogramController.getHistogram(fulltext,facets,entities,$scope.currentRange,$scope.currentLoD).get().then(function(respone) {
                        var overallPromise = $q.defer();
                        if($scope.drilldown ||  $scope.drillup) {
                            playRoutes.controllers.HistogramController.getHistogram("",$scope.emptyFacets,[],$scope.currentRange,$scope.currentLoD).get().then(function(responeAll) {
                                $scope.data = [];
                                angular.forEach(responeAll.data.histogram, function(x) {
                                    var count = x.count;
                                    if(x.count == 0)
                                        count = null;
                                    var drilldown = x.range;
                                    if($scope.lod.indexOf($scope.currentLoD) == $scope.lod.length -1) drilldown = false;
                                    $scope.data.push({
                                        name: x.range,
                                        y: count,
                                        drilldown: drilldown,
                                        title: x.range
                                    });
                                });
                                overallPromise.resolve('success');
                            });
                        } else {
                            overallPromise.resolve('success');
                        }

                        overallPromise.promise.then(function() {
                            $scope.dataFilter = [];
                            angular.forEach(respone.data.histogram, function(x) {
                                var count = x.count;
                                if(x.count == 0)
                                    count = null;
                                var drilldown = x.range;
                                if($scope.lod.indexOf($scope.currentLoD) == $scope.lod.length -1) drilldown = false;
                                $scope.dataFilter.push({
                                    name: x.range,
                                    y: count,
                                    drilldown: drilldown,
                                    title: x.range
                                });
                            });
                            if(!$scope.initialized)  {
                                $scope.data = angular.copy($scope.dataFilter);
                                $scope.initHistogram();
                            }
                            else if(!$scope.drilldown && !$scope.drillup) {
                                var name = "Overview";
                                if($scope.currentRange) name = $scope.currentRange;
                                var series = {
                                    data: $scope.dataFilter,
                                    name: name,
                                    dataLabels: {
                                        inside: true,
                                        verticalAlign: "top",
                                        useHTML: true,
                                        formatter : function() {
                                            return $('<div/>').css({
                                                'color' : 'white'
                                            }).text(this.y)[0].outerHTML;
                                        }
                                    },
                                    color: 'black',
                                    cursor: 'pointer',
                                    point: {
                                        events: {
                                            click: function () {
                                                $scope.clickedItem(this);
                                            }
                                        }
                                    }
                                };
                                if($scope.histogram.series[1])
                                    $scope.histogram.series[1].setData($scope.dataFilter);
                                else
                                    $scope.histogram.addSeries(series);
                            }


                            $scope.histogram.hideLoading();

                            deferred.resolve('success');
                        });

                    });
                    return deferred.promise;
                };

                $scope.updateLoD = function(lod) {
                    $scope.currentLoD = lod;
                };



                $scope.observer.registerObserverCallback(function() {
                    if(!$scope.drilldown && !$scope.drillup)
                        $scope.updateHistogram()
                });

                $scope.drillDown = function(e, chart) {
                    console.log("histogram drilldown");

                    if (!e.seriesOptions) {
                        $scope.drilldown = true;
                        $scope.currentLoD = $scope.lod[$scope.lod.indexOf($scope.currentLoD) + 1];
                        if($scope.lod.indexOf($scope.currentLoD) == 0)
                            $scope.currentRange = "";
                        else
                            $scope.currentRange = e.point.name;
                        //$scope.addTimeFilter(e.point.name);
                        $scope.updateHistogram().then(function (res) {

                            $scope.drilldown = false;
                            var series = [{
                                name: e.point.name,
                                data: $scope.data,
                                color: 'rgb(149, 206, 255)',
                                cursor: 'pointer',
                                point: {
                                    events: {
                                        click: function(e) {
                                            $scope.clickedItem(this);
                                        }
                                    }
                                }
                            },
                                {
                                name: e.point.name,
                                data: $scope.dataFilter,
                                color: 'black',
                                cursor: 'pointer',
                                point: {
                                    events: {
                                        click: function(e) {
                                            $scope.clickedItem(this);
                                        }
                                    }
                                },
                                dataLabels: {
                                    inside: true,
                                    verticalAlign: "top",
                                    useHTML: true,
                                    formatter : function() {
                                        return $('<div/>').css({
                                            'color' : 'white'
                                        }).text(this.y)[0].outerHTML;
                                    }
                                }
                            }
                            ];
                            chart.addSingleSeriesAsDrilldown(e.point, series[0]);
                            chart.addSingleSeriesAsDrilldown(e.point, series[1]);
                            chart.applyDrilldown();
                        });
                    }
                };

                $scope.drillUp = function(e) {
                    if (!$scope.drillup) {
                        console.log("histogram drillup");
                        $scope.drillup = true;
                        $scope.currentLoD = $scope.lod[$scope.lod.indexOf($scope.currentLoD) - 1];
                        $scope.observer.drillUpTimeFilter();
                        if ($scope.lod.indexOf($scope.currentLoD) == 0)
                            $scope.currentRange = "";
                        else
                            $scope.currentRange = $scope.observer.getTimeRange();
                        $scope.updateHistogram().then(function () {
                            $scope.histogram.series[0].setData($scope.data);
                            var series = {
                                data: $scope.dataFilter,
                                name:  $scope.currentLoD,
                                cursor: 'pointer',
                                point: {
                                    events: {
                                        click: function () {
                                            $scope.clickedItem(this);
                                        }
                                    }
                                }
                            };
                            if ($scope.histogram.series[1])
                                $scope.histogram.series[1].setData($scope.dataFilter);
                            else
                                $scope.histogram.addSeries(series);
                            $scope.drillup = false;

                        });
                    }
                }
            }
        ])
});
