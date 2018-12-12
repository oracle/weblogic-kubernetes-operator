/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

var noResult = {l: "No results found"};
var category = "category";
var catModules = "Modules";
var catPackages = "Packages";
var catTypes = "Types";
var catMembers = "Members";
var catSearchTags = "SearchTags";
var highlight = "<span class=\"resultHighlight\">$&</span>";
var camelCaseRegexp = "";
var secondaryMatcher = "";
function getName(name) {
    var anchor = "";
    var ch = '';
    for (i = 0; i < name.length; i++) {
        ch = name.charAt(i);
        switch (ch) {
            case '(':
            case ')':
            case '<':
            case '>':
            case ',':
                anchor += "-";
                break;
            case ' ':
            case '[':
                break;
            case ']':
                anchor += ":A";
                break;
            case '$':
                if (i == 0)
                    anchor += "Z:Z";
                anchor += ":D";
                break;
            case '_':
                if (i == 0)
                    anchor += "Z:Z";
                anchor += ch;
                break;
            default:
                anchor += ch;
        }
    }
    return anchor;
}
function getHighlightedText(item) {
    var ccMatcher = new RegExp(camelCaseRegexp);
    var label = item.replace(ccMatcher, highlight);
    if (label === item) {
        label = item.replace(secondaryMatcher, highlight);
    }
    return label;
}
var watermark = 'Search';
$(function() {
    $("#search").prop("disabled", false);
    $("#reset").prop("disabled", false);
    $("#search").val(watermark).addClass('watermark');
    $("#search").blur(function() {
        if ($(this).val().length == 0) {
            $(this).val(watermark).addClass('watermark');
        }
    });
    $("#search").keydown(function() {
        if ($(this).val() == watermark) {
            $(this).val('').removeClass('watermark');
        }
    });
    $("#reset").click(function() {
        $("#search").val('');
        $("#search").focus();
    });
    $("#search").focus();
    $("#search")[0].setSelectionRange(0, 0);
});
$.widget("custom.catcomplete", $.ui.autocomplete, {
    _create: function() {
        this._super();
        this.widget().menu("option", "items", "> :not(.ui-autocomplete-category)");
    },
    _renderMenu: function(ul, items) {
        var rMenu = this,
                currentCategory = "";
        $.each(items, function(index, item) {
            var li;
            if (item.l !== noResult.l && item.category !== currentCategory) {
                ul.append("<li class=\"ui-autocomplete-category\">" + item.category + "</li>");
                currentCategory = item.category;
            }
            li = rMenu._renderItemData(ul, item);
            if (item.category) {
                li.attr("aria-label", item.category + " : " + item.l);
                li.attr("class", "resultItem");
            } else {
                li.attr("aria-label", item.l);
                li.attr("class", "resultItem");
            }
        });
    },
    _renderItem: function(ul, item) {
        var label = "";
        if (item.category === catModules) {
            label = getHighlightedText(item.l);
        } else if (item.category === catPackages) {
            label = (item.m)
                    ? getHighlightedText(item.m + "/" + item.l)
                    : getHighlightedText(item.l);
        } else if (item.category === catTypes) {
            label = getHighlightedText(item.p + "." + item.l);
        } else if (item.category === catMembers) {
            label = getHighlightedText(item.p + "." + (item.c + "." + item.l));
        } else if (item.category === catSearchTags) {
            label = getHighlightedText(item.l);
        } else {
            label = item.l;
        }
        $li = $("<li/>").appendTo(ul);
        if (item.category === catSearchTags) {
            if (item.d) {
                $("<a/>").attr("href", "#")
                        .html(label + "<span class=\"searchTagHolderResult\"> (" + item.h + ")</span><br><span class=\"searchTagDescResult\">"
                                + item.d + "</span><br>")
                        .appendTo($li);
            } else {
                $("<a/>").attr("href", "#")
                        .html(label + "<span class=\"searchTagHolderResult\"> (" + item.h + ")</span>")
                        .appendTo($li);
            }
        } else {
            $("<a/>").attr("href", "#")
                    .html(label)
                    .appendTo($li);
        }
        return $li;
    }
});
$(function() {
    $("#search").catcomplete({
        minLength: 1,
        delay: 100,
        source: function(request, response) {
            var result = new Array();
            var presult = new Array();
            var tresult = new Array();
            var mresult = new Array();
            var tgresult = new Array();
            var secondaryresult = new Array();
            var displayCount = 0;
            var exactMatcher = new RegExp("^" + $.ui.autocomplete.escapeRegex(request.term) + "$", "i");
            camelCaseRegexp = ($.ui.autocomplete.escapeRegex(request.term)).split(/(?=[A-Z])/).join("([a-z0-9_$]*?)");
            var camelCaseMatcher = new RegExp("^" + camelCaseRegexp);
            secondaryMatcher = new RegExp($.ui.autocomplete.escapeRegex(request.term), "i");

            // Return the nested innermost name from the specified object
            function nestedName(e) {
                return e.l.substring(e.l.lastIndexOf(".") + 1);
            }

            // Sort array items by short name (as opposed to fully qualified name).
            // Additionally, sort by the nested type name, when present,
            // as opposed to top level short name.
            function sortAndConcatResults(a1, a2) {
                var sortingKey;
                var sortArray = function(e1, e2) {
                    var l = sortingKey(e1);
                    var m = sortingKey(e2);
                    if (l < m)
                        return -1;
                    if (l > m)
                        return 1;
                    return 0;
                };
                sortingKey = function(e) {
                    return nestedName(e).toUpperCase();
                };
                a1.sort(sortArray);
                a2.sort(sortArray);
                a1 = a1.concat(a2);
                a2.length = 0;
                return a1;
            }

            if (moduleSearchIndex) {
                var mdleCount = 0;
                $.each(moduleSearchIndex, function(index, item) {
                    item[category] = catModules;
                    if (exactMatcher.test(item.l)) {
                        result.unshift(item);
                        mdleCount++;
                    } else if (camelCaseMatcher.test(item.l)) {
                        result.unshift(item);
                    } else if (secondaryMatcher.test(item.l)) {
                        secondaryresult.push(item);
                    }
                });
                displayCount = mdleCount;
                result = sortAndConcatResults(result, secondaryresult);
            }
            if (packageSearchIndex) {
                var pCount = 0;
                var pkg = "";
                $.each(packageSearchIndex, function(index, item) {
                    item[category] = catPackages;
                    pkg = (item.m)
                            ? (item.m + "/" + item.l)
                            : item.l;
                    if (exactMatcher.test(item.l)) {
                        presult.unshift(item);
                        pCount++;
                    } else if (camelCaseMatcher.test(pkg)) {
                        presult.unshift(item);
                    } else if (secondaryMatcher.test(pkg)) {
                        secondaryresult.push(item);
                    }
                });
                result = result.concat(sortAndConcatResults(presult, secondaryresult));
                displayCount = (pCount > displayCount) ? pCount : displayCount;
            }
            if (typeSearchIndex) {
                var tCount = 0;
                $.each(typeSearchIndex, function(index, item) {
                    item[category] = catTypes;
                    var s = nestedName(item);
                    if (exactMatcher.test(s)) {
                        tresult.unshift(item);
                        tCount++;
                    } else if (camelCaseMatcher.test(s)) {
                        tresult.unshift(item);
                    } else if (secondaryMatcher.test(item.p + "." + item.l)) {
                        secondaryresult.push(item);
                    }
                });
                result = result.concat(sortAndConcatResults(tresult, secondaryresult));
                displayCount = (tCount > displayCount) ? tCount : displayCount;
            }
            if (memberSearchIndex) {
                var mCount = 0;
                $.each(memberSearchIndex, function(index, item) {
                    item[category] = catMembers;
                    var s = nestedName(item);
                    if (exactMatcher.test(s)) {
                        mresult.unshift(item);
                        mCount++;
                    } else if (camelCaseMatcher.test(s)) {
                        mresult.unshift(item);
                    } else if (secondaryMatcher.test(item.c + "." + item.l)) {
                        secondaryresult.push(item);
                    }
                });
                result = result.concat(sortAndConcatResults(mresult, secondaryresult));
                displayCount = (mCount > displayCount) ? mCount : displayCount;
            }
            if (tagSearchIndex) {
                var tgCount = 0;
                $.each(tagSearchIndex, function(index, item) {
                    item[category] = catSearchTags;
                    if (exactMatcher.test(item.l)) {
                        tgresult.unshift(item);
                        tgCount++;
                    } else if (secondaryMatcher.test(item.l)) {
                        secondaryresult.push(item);
                    }
                });
                result = result.concat(sortAndConcatResults(tgresult, secondaryresult));
                displayCount = (tgCount > displayCount) ? tgCount : displayCount;
            }
            displayCount = (displayCount > 500) ? displayCount : 500;
            var counter = function() {
                var count = {Modules: 0, Packages: 0, Types: 0, Members: 0, SearchTags: 0};
                var f = function(item) {
                    count[item.category] += 1;
                    return (count[item.category] <= displayCount);
                };
                return f;
            }();
            response(result.filter(counter));
        },
        response: function(event, ui) {
            if (!ui.content.length) {
                ui.content.push(noResult);
            } else {
                $("#search").empty();
            }
        },
        autoFocus: true,
        position: {
            collision: "flip"
        },
        select: function(event, ui) {
            if (ui.item.l !== noResult.l) {
                var url = "";
                if (ui.item.category === catModules) {
                    url = ui.item.l + "-summary.html";
                } else if (ui.item.category === catPackages) {
                    url = ui.item.l.replace(/\./g, '/') + "/package-summary.html";
                } else if (ui.item.category === catTypes) {
                    if (ui.item.p === "<Unnamed>") {
                        url = ui.item.l + ".html";
                    } else {
                        url = ui.item.p.replace(/\./g, '/') + "/" + ui.item.l + ".html";
                    }
                } else if (ui.item.category === catMembers) {
                    if (ui.item.p === "<Unnamed>") {
                        url = ui.item.c + ".html" + "#";
                    } else {
                        url = ui.item.p.replace(/\./g, '/') + "/" + ui.item.c + ".html" + "#";
                    }
                    if (ui.item.url) {
                        url += ui.item.url;
                    } else {
                        url += getName(ui.item.l);
                    }
                } else if (ui.item.category === catSearchTags) {
                    url += ui.item.u;
                }
                if (top !== window) {
                    parent.classFrame.location = pathtoroot + url;
                } else {
                    window.location.href = pathtoroot + url;
                }
            }
        }
    });
});
