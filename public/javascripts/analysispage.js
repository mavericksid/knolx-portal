$(function () {

    var startDate = moment().subtract(3, 'months').startOf('day').format('YYYY-MM-DD HH:mm').toString();
    var endDate = moment().endOf('day').format('YYYY-MM-DD HH:mm ').toString();

    analysis(startDate, endDate);

    $('#demo').daterangepicker({
        "startDate": moment().subtract(1, 'months'),
        "endDate": moment().format('DD-MM-YYYY')
    }, function (start, end, label) {
        var startDate = start.format('YYYY-MM-DD h:mm A');
        var endDate = end.format('YYYY-MM-DD h:mm A');

        analysis(startDate, endDate);
    });

});

function analysis(startDate, EndDate) {

     pieChart(startDate, EndDate);
     columnChart(startDate, EndDate);
     lineGraph(startDate, EndDate);
}

function columnChart(startDate, EndDate) {

        var formData = {
            "startDate": startDate,
            "endDate"  : EndDate
            };
    jsRoutes.controllers.KnolxAnalysisController.renderColumnChart().ajax(
            {
                type: 'POST',
                processData: false,
                contentType: 'application/json',
                data: JSON.stringify(formData),
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;
                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (data) {

                var values = JSON.parse(data);
            var subCategoryData = [];
            var columnGraphXAxis = [];

    for (var i = 0; i < values.length; i++) {
        var dataSub = values[i].subCategoryName;
        var sessionSub = values[i].totalSessionSubCategory;

        subCategoryData.push(dataSub);
        columnGraphXAxis.push(sessionSub);
    }

    var columnGraph = Highcharts.chart('column-graph', {
        title: {
            text: 'Knolx Session Sub-Category Analysis'
        },

        subtitle: {
            text: 'Plain'
        },

        xAxis: {
            categories: subCategoryData
        },

        series: [{
            type: 'column',
            colorByPoint: true,
            data: columnGraphXAxis,
            showInLegend: false
        }]
    });
    }
    });

}


function pieChart(startDate, EndDate) {
    var formData = {
            "startDate": startDate,
            "endDate"  : EndDate
            };

    jsRoutes.controllers.KnolxAnalysisController.renderPieChart().ajax(
                {
                    type: 'POST',
                    processData: false,
                    contentType: 'application/json',
                    data: JSON.stringify(formData),
                    beforeSend: function (request) {
                        var csrfToken = document.getElementById('csrfToken').value;
                        return request.setRequestHeader('CSRF-Token', csrfToken);
                    },
                    success: function (data) {
                    console.log(data);
                    var values = JSON.parse(data);
                    console.log(values);
                    var items = [];
                     var series = [];

    var categoryInfo = values['categoryInformation'];

    for (var i = 0; i < categoryInfo.length; i++) {
        var dataSubCategory = [];
        var item = {
            "name": categoryInfo[i].categoryName,
            "y": parseFloat(categoryInfo[i].totalSessionCategory / values.totalSession),
            "drilldown": categoryInfo[i].categoryName
        };
        items.push(item);

        for (var j = 0; j < categoryInfo[i]["subCategoryInfo"].length; j++) {
            var dataSub = [categoryInfo[i]["subCategoryInfo"][j].subCategoryName, categoryInfo[i]["subCategoryInfo"][j].totalSessionSubCategory];
            dataSubCategory.push(dataSub);
        }
        var drillData = {
            "name": categoryInfo[i].categoryName,
            "id": categoryInfo[i].categoryName,
            "data": dataSubCategory
        };
        series.push(drillData);
    }

    var column = Highcharts.chart('pie-chart', {
        chart: {
            type: 'pie'
        },
        title: {
            text: 'Knolx Session Analysis'
        },
        plotOptions: {
            pie: {
                allowPointSelect: true,
                cursor: 'pointer',
                depth: 35,
                dataLabels: {
                    enabled: false
                },
                showInLegend: true
            }
        },

        tooltip: {
            headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
            pointFormat: '<span style="color:{point.color}">{point.name}</span>: <b>{point.percentage:.1f}%</b> of total<br/>'
        },
        series: [{
            name: 'Primary Category',
            colorByPoint: true,
            data: items
        }],
        drilldown: {
            series: series
        },
        responsive: {
            rules: [{
                condition: {
                    maxWidth: 500
                },
                chartOptions: {
                    legend: {
                        enabled: false
                    }
                }
            }]
        }
    });
}
})
}


function lineGraph(startDate, EndDate) {

        var formData = {
            "startDate": startDate,
            "endDate"  : EndDate
            };
    jsRoutes.controllers.KnolxAnalysisController.renderLineChart().ajax(
    {
        type: 'POST',
        processData: false,
        contentType: 'application/json',
        data: JSON.stringify(formData),
    beforeSend: function (request) {
        var csrfToken = document.getElementById('csrfToken').value;
        return request.setRequestHeader('CSRF-Token', csrfToken);
    },
    success: function (data) {

    var values = JSON.parse(data);

    var seriesData = [];
    var xAxisData = [];

    for (var i = 0; i < values.length; i++) {

        xAxisData.push(values[i].monthName);
        seriesData.push(values[i].total);
    }

    Highcharts.chart('line-graph', {
        chart: {
            type: 'area'
        },
        title: {
            text: 'Knolx'
        },
        xAxis: {
            categories: xAxisData
        },
        yAxis: {
            title: {
                text: 'Total Session In Month'
            },
            labels: {
                formatter: function () {
                    return this.value;
                }
            }
        },
        tooltip: {
            split: true,
            valueSuffix: ' Sessions'
        },
        plotOptions: {
            area: {
                stacking: 'normal',
                lineColor: '#66FF66',
                lineWidth: 2,
                marker: {
                    lineWidth: 2,
                    lineColor: '#ff6666'
                }
            }
        },
        series: [{
            name: 'Monthly Information',
            data: seriesData
        }]
    });
}
})
}

