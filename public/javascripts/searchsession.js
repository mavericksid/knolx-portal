$(function () {
    $('#search-text').keyup(function () {
        slide(this.value, 1);
    });
});

function slide(keyword, pageNumber) {
    var email = keyword;

    var formData = new FormData();
    formData.append("email", email);
    formData.append("page", pageNumber);

    jsRoutes.controllers.SessionsController.searchSessions().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                var sessionInfo = JSON.parse(data);
                var sessions = sessionInfo["sessions"];
                var page = sessionInfo["page"];
                var pages = sessionInfo["pages"];
                var usersFound = "";
                if (sessions.length > 0) {
                    for (var session = 0; session < sessions.length; session++) {
                        usersFound += "<tr>" +
                            "<td>" + sessions[session].dateString + "</td>" +
                            "<td>" + sessions[session].session + "</td>" +
                            "<td>" + sessions[session].topic + "</td>" +
                            "<td>" + sessions[session].email + "</td>";

                        if (sessions[session].meetup) {
                            usersFound += '<td><span class="label label-info meetup-session ">Meetup</span></td>';
                        } else {
                            usersFound += '<td><span class="label label-info knolx-session ">Knolx</span></td>';
                        }

                        if (sessions[session].cancelled) {
                            usersFound += "<td class='suspended'>Yes</td>";
                        } else {
                            usersFound += "<td class='active-status'>No</td>";
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                            usersFound += "<td><div><span class='label label-success' >Completed</span></div></td>";
                        } else if(sessions[session].cancelled) {
                            usersFound += "<td><div><span class='label label-warning cancelled-session'>Cancelled</span></div></td>"
                        } else {
                            usersFound += "<td><div><span class='label label-warning' >Pending</span><br/></div></td>";
                        }

                        if (sessions[session].completed && !sessions[session].cancelled) {
                           usersFound += "<td  title='Click here for slides & videos' class='clickable-row'>" +
                            "<a href='" + jsRoutes.controllers.SessionsController.shareContent(sessions[session].id)['url'] +
                            "' style='text-decoration: none;'><span class='label more-detail-session'>Click here</span></a>";
                        } else if(sessions[session].cancelled) {
                            usersFound += "<td title='The session has been cancelled'><span class='label label-warning cancelled-session'>Cancelled</span>";
                        }
                        else if(!sessions[session].completed) {
                           usersFound += "<td title='Wait for session to be completed'><span class='label label-warning'>Pending</span>";
                        }
                        usersFound += "</td></tr>"
                    }

                    $('#user-found').html(usersFound);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            var keyword = document.getElementById('search-text').value;
                            slide(keyword, this.id);
                        });
                    }
                } else {
                    $('#user-found').html(
                        "<tr><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-6'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>Oops! No Record Found</span></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td></tr>"
                    );
                    $('.pagination').html("");
                }
            },
            error: function (er) {
                $('#user-found').html(
                    "<tr><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-6'>" + er.responseText + "</td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td><td align='center' class='col-md-1'></td></tr>"
                );
                $('.pagination').html("");
            }
        });
}
