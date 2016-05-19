/**
 * UI-demo
 * Created by jimily on 16-5-16.
 */

;
!function () {
    var config = {
        msgurl: '私信地址',
        chatlogurl: '聊天记录url前缀',
        aniTime: 200,
        right: -232,
        api: {
            friend: 'friend.json', //好友列表接口
            group: 'group.json', //群组列表接口
            chatlog: 'chatlog.json', //聊天记录接口
            groups: 'groups.json', //群组成员接口
            sendurl: '' //发送消息接口
        },
        user: { //当前用户信息
            name: '游客',
            face: 'images/1.png'
        },

        //自动回复内置文案，也可动态读取数据库配置
        autoReplay: [
            '您好，我现在有事不在，一会再和您联系。',
            '你没发错吧？',
            '洗澡中，请勿打扰，偷窥请购票，个体四十，团体八折，订票电话：一般人我不告诉他！',
            '你好，我是主人的美女秘书，有什么事就跟我说吧，等他回来我会转告他的。',
            '我正在拉磨，没法招呼您，因为我们家毛驴去动物保护协会把我告了，说我剥夺它休产假的权利。',
            '<（@￣︶￣@）>',
            '你要和我说话？你真的要和我说话？你确定自己想说吗？你一定非说不可吗？那你说吧，这是自动回复。',
            '主人正在开机自检，键盘鼠标看好机会出去凉快去了，我是他的电冰箱，我打字比较慢，你慢慢说，别急……',
            '(*^__^*) 嘻嘻，是贤心吗？'
        ],


        chating: {},
        hosts: (function () {
            var dk = location.href.match(/\:\d+/);
            dk = dk ? dk[0] : '';
            return 'http://' + document.domain + dk + '/';
        })(),
        json: function (url, data, callback, error) {
            return $.ajax({
                type: 'POST',
                url: url,
                data: data,
                dataType: 'json',
                success: callback,
                error: error
            });
        },
        stopMP: function (e) {
            e ? e.stopPropagation() : e.cancelBubble = true;
        }
    }, dom = [$(window), $(document), $('html'), $('body')], xxim = {};

    var client = clientIO();

    var friendMsgHandler = {};
    var groupMsgHandler = {};
    var friendMsg = {};
    var groupMsg = {};
    var defaultFriendMsgHandler = function (msg) {
        $("ul.xxim_list:eq(0)")
            .find('li.xxim_childnode[data-id=' + msg.from_user + ']')
            .addClass('has_msg');
    };
    var defaultGroupMsgHandler = function (msg) {
        $("ul.xxim_list:eq(1)")
            .find('li.xxim_childnode[data-id=' + msg.group + ']')
            .addClass('has_msg');
    };

    toastr.jqToastr = function (type, $obj, title) {
        toastr[type]("loading", title).find(".toast-message").html($obj);
    };

    log_html = function (param, type) {
        return '<li class="' + (type === 'me' ? 'layim_chateme' : '') + '">'
            + '<div class="layim_chatuser">'
            + function () {
                if (type === 'me') {
                    return '<span class="layim_chattime">' + param.time + '</span>'
                        + '<span class="layim_chatname">' + param.name + '</span>'
                        + '<img src="' + param.face + '" >';
                } else {
                    return '<img src="' + param.face + '" >'
                        + '<span class="layim_chatname">' + param.name + '</span>'
                        + '<span class="layim_chattime">' + param.time + '</span>';
                }
            }()
            + '</div>'
            + '<div class="layim_chatsay">' + param.content + '<em class="layim_zero"></em></div>'
            + '</li>';
    };

    //主界面tab
    xxim.tabs = function (index) {
        var node = xxim.node;
        node.tabs.eq(index).addClass('xxim_tabnow').siblings().removeClass('xxim_tabnow');
        node.list.eq(index).show().siblings('.xxim_list').hide();
        if (node.list.eq(index).find('li').length === 0) {
            xxim.getDates(index);
        }
    };

    //节点
    xxim.renode = function () {
        xxim.node = {
            tabs: $('#xxim_tabs').find('>span'),
            list: $('.xxim_list'),
            online: $('.xxim_online'),
            onlinetex: $('#xxim_onlinetex'),
            xximon: $('#xxim_on'),
            layimFooter: $('#xxim_bottom'),
            xximHide: $('#xxim_hide'),
            xximSearch: $('#xxim_searchkey'),
            searchMian: $('#xxim_searchmain'),
            closeSearch: $('#xxim_closesearch'),
            layimMin: $('#layim_min'),
            applyAdd: $('#applyAdd'),
            createGroup: $("#xxim_createGroup"),
            setKeyBck: $('#xxim_setKeyBck')
        };
    };

    //主界面缩放
    xxim.expend = function () {
        var node = xxim.node;
        if (xxim.layimNode.attr('state') !== '1') {
            xxim.layimNode.stop().animate({right: config.right}, config.aniTime, function () {
                node.xximon.addClass('xxim_off');
                try {
                    localStorage.layimState = 1;
                } catch (e) {
                }
                xxim.layimNode.attr({state: 1});
                node.layimFooter.addClass('xxim_expend').stop().animate({marginLeft: config.right}, config.aniTime / 2);
                node.xximHide.addClass('xxim_show');
            });
        } else {
            xxim.layimNode.stop().animate({right: 1}, config.aniTime, function () {
                node.xximon.removeClass('xxim_off');
                try {
                    localStorage.layimState = 2;
                } catch (e) {
                }
                xxim.layimNode.removeAttr('state');
                node.layimFooter.removeClass('xxim_expend');
                node.xximHide.removeClass('xxim_show');
            });
            node.layimFooter.stop().animate({marginLeft: 0}, config.aniTime);
        }
    };

    //事件
    xxim.event = function () {
        var node = xxim.node;

        //主界面tab
        node.tabs.eq(0).addClass('xxim_tabnow');
        node.tabs.on('click', function () {
            var othis = $(this), index = othis.index();
            xxim.tabs(index);
        });

        //列表展收
        node.list.on('click', 'h5', function () {
            var othis = $(this), chat = othis.siblings('.xxim_chatlist'), parentss = othis.parent();
            if (parentss.hasClass('xxim_liston')) {
                chat.hide();
                parentss.removeClass('xxim_liston');
            } else {
                chat.show();
                parentss.addClass('xxim_liston');
            }
        });

        node.xximon.on('click', xxim.expend);
        node.xximHide.on('click', xxim.expend);

        //添加好友/群组按钮
        node.applyAdd.on('click', function () {
            jQuery('#myModal').modal('hide');
            var type = $('input:radio[name="addType"]:checked').val();
            var id = $('#addName').val();
            xxim.applyAdd(type, id);
        });

        //创建群组按钮
        node.createGroup.on('click', function () {
            jQuery('#createGroup').modal('hide');
            var id = $('#createGroupName').val();
            var info = $('#createGroupInfo').val();
            xxim.createGroup(id, info);
        });

        //设置密保
        node.setKeyBck.on('click', function () {
            xxim.setKeyBck();
        });

        //搜索
        node.xximSearch.keyup(function () {
            var val = $(this).val().replace(/\s/g, '');
            if (val !== '') {
                node.searchMian.show();
                node.closeSearch.show();
                //此处的搜索ajax参考xxim.getDates
                node.list.eq(3).html('<li class="xxim_errormsg">没有符合条件的结果</li>');
            } else {
                node.searchMian.hide();
                node.closeSearch.hide();
            }
        });
        node.closeSearch.on('click', function () {
            $(this).hide();
            node.searchMian.hide();
            node.xximSearch.val('').focus();
        });

        //弹出聊天窗
        config.chatings = 0;
        node.list.on('click', '.xxim_childnode', function () {
            var othis = $(this);
            xxim.popchatbox(othis);
        });

        //点击最小化栏
        node.layimMin.on('click', function () {
            $(this).hide();
            $('#layim_chatbox').parents('.xubox_layer').show();
        });


        //document事件
        dom[1].on('click', function () {
            $('#layim_sendtype').hide();
        });
    };

    xxim.setKeyBck = function () {
        if (!$('#pwd').val()) {
            toastr.error('请输入密码', '设置密保');
            return;
        }
        Rx.Observable.from($('#set_key_bck .question').toArray())
            .map(function (item) {
                return {
                    key: $(item).val(),
                    value: $(item).parent().find('input').val()
                }
            }).doOnNext(function (item) {
                if (item.key == "unselected") {
                    throw '必须选择三个问题'
                }
                if (!item.value) {
                    throw '必须填写问题答案'
                }
            })
            .reduce((arr, item) => (arr.push(item), arr), [])
            .map(data =>
                data.sort(function (a, b) {
                    return a.key > b.key
                })
                    .map(item => item.key + '=' + item.value)
                    .join('&'))
            .flatMap(function (pwd_bck) {
                var pwd = $('#pwd').val();
                return client.setPwdBck({
                    username: config.user.name,
                    pwd: pwd,
                    pwd_bck: pwd_bck
                })
            })
            .subscribe(
            () => '',
            function (ex) {
                toastr.error(client.IMError[ex.code] || ex, '设置密保');
            },
            function () {
                setTimeout(function () {
                    $('#set_key_bck').modal('hide');
                }, 800);
                toastr.success('success', '设置密保');
            }
        );
    };


    xxim.applyAdd = function (type, id) {
        toastr.options.timeOut = 1000;
        toastr.options.extendedTimeOut = 500;
        if (type == 'friend') {
            client.addFriend({to_user: id}).subscribe(
                function () {
                    toastr.success('apply success');
                }, function (result) {
                    toastr.error('apply error:' + client.IMError[result.code]);
                });
        } else if (type == 'group') {
            client.joinGroup({group: id}).subscribe(
                function () {
                    toastr.success('apply success');
                }, function (result) {
                    toastr.error('apply error:' + client.IMError[result.code]);
                });
        }
    };

    xxim.createGroup = function (id, info) {
        client.addGroup({groupName: id, info: info}).subscribe(
            function () {
                toastr.success('create group success');
            }, function (result) {
                toastr.error('create group error:' + client.IMError[result.code]);
            });
    };

    //请求列表数据
    xxim.getDates = function (index) {
        var api = [client.getFriends, client.getGroups],
            node = xxim.node, myf = node.list.eq(index);
        myf.addClass('loading');
        api[index]().subscribe(function (datas) {
            var i = 0, myflen = datas.length, str = '', item;
            if (myflen) {
                if (index !== 2) {
                    str += '<li data-id="123" class="xxim_parentnode">'
                        + '<h5><i></i><span class="xxim_parentname">' + (index === 0 ? '我的好友' : '我的群组') + '</span><em class="xxim_nums">（' + myflen + '）</em></h5>'
                        + '<ul class="xxim_chatlist">';
                    item = datas;
                    for (var j = 0; j < item.length; j++) {
                        str += '<li data-id="' + item[j] + '" class="xxim_childnode" type="' + (index === 0 ? 'one' : 'group') + '"><img src="images/1.png" class="xxim_oneface"><span class="xxim_onename">' + item[j] + '</span></li>';
                    }
                    str += '</ul></li>';
                } else {
                    str += '<li class="xxim_liston">'
                        + '<ul class="xxim_chatlist">';
                    for (; i < myflen; i++) {
                        str += '<li data-id="' + datas.data[i].id + '" class="xxim_childnode" type="one"><img src="' + datas.data[i].face + '"  class="xxim_oneface"><span  class="xxim_onename">' + datas.data[i].name + '</span><em class="xxim_time">' + datas.data[i].time + '</em></li>';
                    }
                    str += '</ul></li>';
                }
                myf.html(str);
            } else {
                myf.html('<li class="xxim_errormsg">你还没有朋友哦</li>');
            }
            myf.removeClass('loading');

        }, function (result) {
            myf.html('<li class="xxim_errormsg">' + client.IMError[result.code] + '</li>');
            myf.removeClass('loading');
        });
    };
    //初始化窗口格局
    xxim.layinit = function () {
        var node = xxim.node;

        //主界面
        try {
            if (!localStorage.layimState) {
                config.aniTime = 0;
                localStorage.layimState = 1;
            }
            if (localStorage.layimState === '1') {
                xxim.layimNode.attr({state: 1}).css({right: config.right});
                node.xximon.addClass('xxim_off');
                node.layimFooter.addClass('xxim_expend').css({marginLeft: config.right});
                node.xximHide.addClass('xxim_show');
            }
        } catch (e) {
            layer.msg(e.message, 5, -1);
        }
    };


    //弹出聊天窗
    xxim.popchatbox = function (othis) {
        var node = xxim.node, dataId = othis.attr('data-id'), param = {
            id: dataId, //用户ID
            type: othis.attr('type'),
            name: othis.find('.xxim_onename').text(),  //用户名
            face: othis.find('.xxim_oneface').attr('src'),  //用户头像
            href: config.hosts + 'user/' + dataId //用户主页
        }, key = param.type + dataId;
        if (!config.chating[key]) {
            xxim.popchat(param);
            config.chatings++;
        } else {
            xxim.tabchat(param);
        }
        config.chating[key] = param;

        var chatbox = $('#layim_chatbox');
        if (chatbox[0]) {
            node.layimMin.hide();
            chatbox.parents('.xubox_layer').show();
        }
    };

    //聊天窗口
    xxim.popchat = function (param) {
        var node = xxim.node, log = {};

        log.success = function (layero) {
            layer.setMove();

            xxim.chatbox = layero.find('#layim_chatbox');
            log.chatlist = xxim.chatbox.find('.layim_chatmore>ul');

            log.chatlist.html('<li data-id="' + param.id + '" type="' + param.type + '"  id="layim_user' + param.type + param.id + '"><span>' + param.name + '</span><em>×</em></li>');
            xxim.tabchat(param, xxim.chatbox);

            //最小化聊天窗
            xxim.chatbox.find('.layer_setmin').on('click', function () {
                layero.hide();
                node.layimMin.text(xxim.nowchat.name).show();
            });

            //关闭窗口
            xxim.chatbox.find('.layim_close').on('click', function () {
                var indexs = layero.attr('times');
                layer.close(indexs);
                xxim.chatbox = null;

                //取消聊天窗消息处理器
                for (var i in config.chating) {
                    //noinspection JSUnfilteredForInLoop
                    var obj = config.chating[i];
                    if (obj.type === 'group')
                        groupMsgHandler[obj.id] = defaultGroupMsgHandler;
                    else
                        friendMsgHandler[obj.id] = defaultFriendMsgHandler;
                }
                config.chating = {};
                config.chatings = 0;
            });

            //关闭某个聊天
            log.chatlist.on('mouseenter', 'li', function () {
                $(this).find('em').show();
            }).on('mouseleave', 'li', function () {
                $(this).find('em').hide();
            });
            log.chatlist.on('click', 'li em', function (e) {
                var parents = $(this).parent(), dataType = parents.attr('type');
                var dataId = parents.attr('data-id'), index = parents.index();
                var chatlist = log.chatlist.find('li'), indexs;

                //取消聊天窗消息处理器
                if (dataType === 'group')
                    groupMsgHandler[dataId] = defaultGroupMsgHandler;
                else
                    friendMsgHandler[dataId] = defaultFriendMsgHandler;

                config.stopMP(e);

                delete config.chating[dataType + dataId];
                config.chatings--;

                parents.remove();
                $('#layim_area' + dataType + dataId).remove();
                if (dataType === 'group') {
                    $('#layim_group' + dataType + dataId).remove();
                }

                if (parents.hasClass('layim_chatnow')) {
                    if (index === config.chatings) {
                        indexs = index - 1;
                    } else {
                        indexs = index + 1;
                    }
                    xxim.tabchat(config.chating[chatlist.eq(indexs).attr('type') + chatlist.eq(indexs).attr('data-id')]);
                }

                if (log.chatlist.find('li').length === 1) {
                    log.chatlist.parent().hide();
                }
            });

            //聊天选项卡
            log.chatlist.on('click', 'li', function () {
                var othis = $(this), dataType = othis.attr('type'), dataId = othis.attr('data-id');
                xxim.tabchat(config.chating[dataType + dataId]);
            });

            //发送热键切换
            log.sendType = $('#layim_sendtype');
            log.sendTypes = log.sendType.find('span');
            $('#layim_enter').on('click', function (e) {
                config.stopMP(e);
                log.sendType.show();
            });
            log.sendTypes.on('click', function () {
                log.sendTypes.find('i').text('');
                $(this).find('i').text('√');
            });

            xxim.transmit();
        };

        log.html = '<div class="layim_chatbox" id="layim_chatbox">'
            + '<h6>'
            + '<span class="layim_move"></span>'
            + '    <a href="' + param.url + '" class="layim_face" target="_blank"><img src="' + param.face + '" ></a>'
            + '    <a href="' + param.url + '" class="layim_names" target="_blank">' + param.name + '</a>'
            + '    <span class="layim_rightbtn">'
            + '        <i class="layer_setmin"></i>'
            + '        <i class="layim_close"></i>'
            + '    </span>'
            + '</h6>'
            + '<div class="layim_chatmore" id="layim_chatmore">'
            + '    <ul class="layim_chatlist"></ul>'
            + '</div>'
            + '<div class="layim_groups" id="layim_groups"></div>'
            + '<div class="layim_chat">'
            + '    <div class="layim_chatarea" id="layim_chatarea">'
            + '        <ul class="layim_chatview layim_chatthis"  id="layim_area' + param.type + param.id + '"></ul>'
            + '    </div>'
            + '    <div class="layim_tool">'
            + '        <i class="layim_addface" title="发送表情"></i>'
            + '        <a href="javascript:;"><i class="layim_addimage" title="上传图片"></i></a>'
            + '        <a href="javascript:;"><i class="layim_addfile" title="上传附件"></i></a>'
            + '        <a href="" target="_blank" class="layim_seechatlog"><i></i>聊天记录</a>'
            + '    </div>'
            + '    <textarea class="layim_write" id="layim_write"></textarea>'
            + '    <div class="layim_send">'
            + '        <div class="layim_sendbtn" id="layim_sendbtn">发送</div>'
            + '    </div>'
            + '</div>'
            + '</div>';

        if (config.chatings < 1) {
            $.layer({
                type: 1,
                border: [0],
                title: false,
                shade: [0],
                area: ['620px', '493px'],
                move: ['.layim_chatbox .layim_move', true],
                moveType: 1,
                closeBtn: false,
                offset: [(($(window).height() - 493) / 2) + 'px', ''],
                page: {
                    html: log.html
                }, success: function (layero) {
                    log.success(layero);
                }
            })
        } else {
            log.chatmore = xxim.chatbox.find('#layim_chatmore');
            log.chatarea = xxim.chatbox.find('#layim_chatarea');

            log.chatmore.show();

            log.chatmore.find('ul>li').removeClass('layim_chatnow');
            log.chatmore.find('ul').append('<li data-id="' + param.id + '" type="' + param.type + '" id="layim_user' + param.type + param.id + '" class="layim_chatnow"><span>' + param.name + '</span><em>×</em></li>');

            log.chatarea.find('.layim_chatview').removeClass('layim_chatthis');
            log.chatarea.append('<ul class="layim_chatview layim_chatthis" id="layim_area' + param.type + param.id + '"></ul>');

            xxim.tabchat(param);
        }

        //群组
        log.chatgroup = xxim.chatbox.find('#layim_groups');
        if (param.type === 'group') {
            log.chatgroup.find('ul').removeClass('layim_groupthis');
            log.chatgroup.append('<ul class="layim_groupthis" id="layim_group' + param.type + param.id + '"></ul>');
            xxim.getGroups(param);
        }
        //点击群员切换聊天窗
        log.chatgroup.on('click', 'ul>li', function () {
            xxim.popchatbox($(this));
        });


        //将本地聊天记录加载出来
        var keys = xxim.nowchat.type + xxim.nowchat.id;
        var imarea = xxim.chatbox.find('#layim_area' + keys);
        var msgs = param.type === 'group' ? groupMsg[xxim.nowchat.id] : friendMsg[xxim.nowchat.id];
        if (msgs) {
            msgs.forEach(function (item) {
                imarea.append(log_html({
                    time: item.date_time,
                    name: item.from_user,
                    face: config.user.face,
                    content: item.content
                }, item.me));
            });
            imarea.scrollTop(imarea[0].scrollHeight);
        }

        var msgHandler = function (msg) {
            imarea.append(log_html({
                time: msg.date_time,
                name: msg.from_user,
                face: config.user.face,
                content: msg.content
            }));

            imarea.scrollTop(imarea[0].scrollHeight);
        };

        if (param.type === 'group') {
            //去除小红点
            $("ul.xxim_list:eq(1)")
                .find('li.xxim_childnode[data-id=' + xxim.nowchat.id + ']')
                .removeClass('has_msg');
            groupMsgHandler[xxim.nowchat.id] = msgHandler;
        } else {
            //去除小红点
            $("ul.xxim_list:eq(0)")
                .find('li.xxim_childnode[data-id=' + xxim.nowchat.id + ']')
                .removeClass('has_msg');
            friendMsgHandler[xxim.nowchat.id] = msgHandler;
        }
    };


    //消息传输
    xxim.transmit = function () {
        var node = xxim.node, log = {};
        node.sendbtn = $('#layim_sendbtn');
        node.imwrite = $('#layim_write');

        //发送
        log.send = function () {
            //var data = {
            //    content: node.imwrite.val(),
            //    id: xxim.nowchat.id,
            //    sign_key: '', //密匙
            //    _: +new Date
            //};
            var data = {
                content: node.imwrite.val(),
                from_user: config.user.name,
                to_user: xxim.nowchat.name
            };

            if (data.content.replace(/\s/g, '') === '') {
                layer.tips('说点啥呗！', '#layim_write', 2);
                node.imwrite.focus();
            } else {
                //此处皆为模拟
                var keys = xxim.nowchat.type + xxim.nowchat.id;
                //聊天模版


                log.imarea = xxim.chatbox.find('#layim_area' + keys);

                log.imarea.append(log_html({
                    time: utils.date(),
                    name: config.user.name,
                    face: config.user.face,
                    content: data.content
                }, 'me'));
                node.imwrite.val('').focus();
                log.imarea.scrollTop(log.imarea[0].scrollHeight);

                if (xxim.nowchat.type == 'group') {
                    data.group = data.to_user;
                    client.sendGroupMessage(data).subscribe(function () {
                    }, function (rs) {
                        toastr.options.timeOut = 0;
                        toastr.options.extendedTimeOut = 0;
                        toastr.jqToastr('error', client.IMError[rs.code], '系统通知');
                    }, function () {
                        data.me = "me";
                        data.date_time = utils.date();
                        groupMsg[data.group] = groupMsg[data.group] || [];
                        groupMsg[data.group].push(data);
                    });
                } else {
                    client.sendPrivateMessage(data).subscribe(function () {
                        data.me = "me";
                        data.date_time = utils.date();
                        friendMsg[data.from_user] = friendMsg[data.from_user] || [];
                        friendMsg[data.from_user].push(data);
                    }, function (rs) {
                        toastr.options.timeOut = 0;
                        toastr.options.extendedTimeOut = 0;
                        toastr.jqToastr('error', client.IMError[rs.code], '系统通知');
                    });
                }
                //setTimeout(function () {
                //    log.imarea.append(log_html({
                //        time: '2014-04-26 0:38',
                //        name: xxim.nowchat.name,
                //        face: xxim.nowchat.face,
                //        content: config.autoReplay[(Math.random() * config.autoReplay.length) | 0]
                //    }));
                //    log.imarea.scrollTop(log.imarea[0].scrollHeight);
                //}, 500);

            }

        };
        node.sendbtn.on('click', log.send);

        node.imwrite.keyup(function (e) {
            if (e.keyCode === 13) {
                log.send();
            }
        });
    };

    //定位到某个聊天队列
    xxim.tabchat = function (param) {
        var node = xxim.node, log = {}, keys = param.type + param.id;
        xxim.nowchat = param;

        xxim.chatbox.find('#layim_user' + keys).addClass('layim_chatnow').siblings().removeClass('layim_chatnow');
        xxim.chatbox.find('#layim_area' + keys).addClass('layim_chatthis').siblings().removeClass('layim_chatthis');
        xxim.chatbox.find('#layim_group' + keys).addClass('layim_groupthis').siblings().removeClass('layim_groupthis');

        xxim.chatbox.find('.layim_face>img').attr('src', param.face);
        xxim.chatbox.find('.layim_face, .layim_names').attr('href', param.href);
        xxim.chatbox.find('.layim_names').text(param.name);

        xxim.chatbox.find('.layim_seechatlog').attr('href', config.chatlogurl + param.id);

        log.groups = xxim.chatbox.find('.layim_groups');
        if (param.type === 'group') {
            log.groups.show();
        } else {
            log.groups.hide();
        }

        $('#layim_write').focus();

    };


    //渲染骨架
    xxim.view = function () {
        var xximNode = xxim.layimNode = $('<div id="xximmm" class="xxim_main">'
            + '<div class="xxim_top" id="xxim_top">'
            + '  <div class="xxim_tabs" id="xxim_tabs"><span class="xxim_tabfriend" title="好友"><i></i></span><span class="xxim_tabgroup" title="群组"><i></i></span><span class="xxim_latechat"  title="最近聊天"><i></i></span></div>'
            + '  <ul class="xxim_list" style="display:block"></ul>'
            + '  <ul class="xxim_list"></ul>'
            + '  <ul class="xxim_list"></ul>'
            + '  <ul class="xxim_list xxim_searchmain" id="xxim_searchmain"></ul>'
            + '</div>'
            + '<ul class="xxim_bottom" id="xxim_bottom">'
            + '<li class="xxim_online" id="xxim_online" data-toggle="modal" data-target="#myModal">添加'
            + '<li class="xxim_seter" id="xxim_seter" title="创建群组" data-toggle="modal" data-target="#createGroup">创建'
            + '<li class="xxim_seter" id="xxim_set_key_bck" title="设置密保" data-toggle="modal" data-target="#set_key_bck">'
            + '<i></i>'
            + '<div class="">'
            + '</div>'
            + '</li>'
            + '<li class="xxim_hide" id="xxim_hide" title="退出"><i></i></li>'
            + '<li id="xxim_on" class="xxim_icon xxim_on"></li>'
            + '<div class="layim_min" id="layim_min"></div>'
            + '</ul>'
            + '</div>');
        dom[3].append(xximNode);

        xxim.renode();
        xxim.getDates(0);
        xxim.event();
        xxim.layinit();
    };

    xxim.init = (function () {

        //注册/登录切换
        $("#go_to_register").on("click", function () {
            $("#login").hide();
            $("#register").show();
        });
        $("#register a").on("click", function () {
            $("#login").show();
            $("#register").hide();
        });

        $("#register button").on("click", function () {
            var username = $("#register_username").val();
            var password = $("#register_pwd").val();

            client
                .register(true, {username: username, pwd: password})
                .subscribe(
                function () {

                    toastr.options.timeOut = 1000;
                    toastr.options.extendedTimeOut = 500;
                    toastr.success("register success");
                    $("#login").show();
                    $("#register").hide();
                },
                function (result) {
                    alert(client.IMError[result.code]);
                }
            );
        });

        $("#login button").on("click", function () {
            var username = $("#login_username").val();
            var password = $("#login_pwd").val();

            client
                .login({username: username, pwd: password})
                .subscribe(
                function () {
                    toastr.options.timeOut = 1000;
                    toastr.options.extendedTimeOut = 500;
                    toastr.success("login success");
                    config.user = { //当前用户信息
                        name: username,
                        face: 'images/1.png'
                    };
                    $("#login").hide();
                    xxim.view();

                    //注册消息处理器
                    client.getFriends()
                        .flatMap(Rx.Observable.from)
                        .subscribe(function (friend) {
                            friendMsgHandler[friend] = defaultFriendMsgHandler;
                        });
                    client.getGroups()
                        .flatMap(Rx.Observable.from)
                        .subscribe(function (group) {
                            groupMsgHandler[group] = defaultGroupMsgHandler;
                        });


                    client.onMsg()
                        .subscribe(function (msg) {
                            switch (msg.type) {
                                case client.messageType.FRIEND_MESSAGE :
                                    friendMsg[msg.from_user] = friendMsg[msg.from_user] || [];
                                    friendMsg[msg.from_user].push(msg);
                                    friendMsgHandler[msg.from_user](msg);
                                    break;
                                case client.messageType.GROUP_MESSAGE:
                                    groupMsg[msg.group] = groupMsg[msg.group] || [];
                                    groupMsg[msg.group].push(msg);
                                    groupMsgHandler[msg.group](msg);
                                    break;
                                case client.messageType.ADD_FRIEND:
                                    toastr.options.timeOut = 0;
                                    toastr.options.extendedTimeOut = 0;
                                    var addFriendUI =
                                        $('<div>').addClass('row-fluid').text('“' + msg.from_user + '”添加你为好友').add(
                                            $('<div>').append(
                                                $('<button type="button" class="btn col-md-4">拒绝</button>').on('click', function () {
                                                    client.replyAddFriend({
                                                        to_user: msg.from_user,
                                                        content: 'NO'
                                                    }).subscribe();
                                                })
                                            ).append(
                                                $('<button type="button" class="btn btn-primary col-md-4 col-md-offset-1">同意</button>').on('click', function () {
                                                    client.replyAddFriend({
                                                        to_user: msg.from_user,
                                                        content: 'YES'
                                                    }).subscribe();
                                                    xxim.getDates(0);

                                                })
                                            )
                                        );
                                    toastr.jqToastr('info', addFriendUI, '好友申请');
                                    break;
                                case client.messageType.REPLY_ADD_FRIEND:
                                    xxim.getDates(0);
                                    toastr.options.timeOut = 0;
                                    toastr.options.extendedTimeOut = 0;
                                    var replyAddGriendUI = $('<div>').addClass('row-fluid').text('“' + msg.from_user + '”回复您的好友申请：' + msg.content);
                                    toastr.jqToastr('info', replyAddGriendUI, '好友申请回复');
                                    break;
                                case client.messageType.JOIN_GROUP:
                                    toastr.options.timeOut = 0;
                                    toastr.options.extendedTimeOut = 0;
                                    var joinGroupUI =
                                        $('<div>').addClass('row-fluid').text('“' + msg.from_user + '”申请加入群：' + msg.group).add(
                                            $('<div>').append(
                                                $('<button type="button" class="btn col-md-4">拒绝</button>').on('click', function () {
                                                    client.replyOfJoinGroup({
                                                        group: msg.group,
                                                        to_user: msg.from_user,
                                                        content: 'NO'
                                                    }).subscribe();
                                                })
                                            ).append(
                                                $('<button type="button" class="btn btn-primary col-md-4 col-md-offset-1">同意</button>').on('click', function () {
                                                    client.replyOfJoinGroup({
                                                        group: msg.group,
                                                        to_user: msg.from_user,
                                                        content: 'YES'
                                                    }).subscribe();

                                                })
                                            )
                                        );
                                    toastr.jqToastr('info', joinGroupUI, '入群申请');
                                    break;
                                case client.messageType.REPLY_JOIN_GROUP:
                                    xxim.getGroups({type: 'group', id: msg.group});
                                    toastr.options.timeOut = 0;
                                    toastr.options.extendedTimeOut = 0;
                                    var replyJoinGroupUI = $('<div>').addClass('row-fluid').text('“' + msg.from_user + '”回复您的入群申请：' + msg.content);
                                    toastr.jqToastr('info', replyJoinGroupUI, '申请加入' + msg.group + '群组的回复');
                                    break;
                                case client.messageType.DELETE_FRIEND:
                                    xxim.getDates(0);
                                    break;
                                case client.messageType.DELETE_GROUP_MEMBER:
                                    xxim.getGroups({type: 'group', id: msg.group});
                                    break;
                                case client.messageType.EXIT_GROUP:
                                    xxim.getGroups({type: 'group', id: msg.group});
                                    break;
                                case client.messageType.NOTIFICATION:
                                    toastr.options.timeOut = 0;
                                    toastr.options.extendedTimeOut = 0;
                                    toastr.jqToastr('info', msg.content, '系统通知');
                                    break;
                            }
                        });

                },
                function (result) {
                    alert(client.IMError[result.code]);
                }
            );
        });

        $("#login").show();
        $("#register").hide();
    })();

    //请求群员
    xxim.getGroups = function (param) {
        var keys = param.type + param.id, str = '',
            groupss = xxim.chatbox.find('#layim_group' + keys);
        if (!groupss)
            return;
        groupss.addClass('loading');
        client.getMembersOfGroup(param.id)
            .subscribe(function (data) {
                if (data.length) {
                    data.forEach(function (item) {
                        str += '<li data-id="' + item + '" type="one"><img src="' + config.user.face + '"><span class="xxim_onename">' + item + '</span></li>';
                    });
                } else {
                    str = '<li class="layim_errors">没有群员</li>';
                }
                groupss.removeClass('loading');
                groupss.html(str);
            }, function () {
                groupss.removeClass('loading');
                groupss.html('<li class="layim_errors">请求异常</li>');
            });
    };

    var questionList = [
        '您母亲的姓名是？',
        '您父亲的姓名是？',
        '您配偶的姓名是？',
        '您的出生地是？',
        '您父亲的生日是？',
        '您母亲的生日是？',
        '您自己的生日是？',
        '您的偶像是？'
    ];

    var list = $('.question').on('change', function () {
        list.each(function (ignore, element) {
            element = $(element);
            updateSelect(element);
        });
    });
    var updateSelect = function (element) {
        var values = list.toArray()
            .map(item => $(item).val())
            .filter(val => val != element.val() && val != null && val != "unselected");

        var selectedVal = element.val();

        element.html('');

        if (selectedVal == null || selectedVal == "unselected") {
            element.append(
                $('<option selected="selected" value="unselected">-----请选择-----</option>')
            );
        }

        questionList
            .filter(val => !values.includes(val))
            .forEach(function (item) {
                element.append(
                    $('<option>')
                        .val(item)
                        .text(item)
                        .attr('selected', selectedVal == item ? 'selected' : null)
                );
            });
    };
    list.each(function (ignore, element) {
        element = $(element);
        updateSelect(element);
    });

    setNewPwd = function () {
        if (!$("#username").val()) {
            toastr.error('用户名不能为空', '设置新密码');
            return;
        }
        Rx.Observable.from($('#set_new_pwd .question').toArray())
            .map(function (item) {
                return {
                    key: $(item).val(),
                    value: $(item).parent().find('input').val()
                }
            })
            .doOnNext(function (item) {
                if (item.key == "unselected") {
                    throw '必须选择三个问题'
                }
                if (!item.value) {
                    throw '必须填写问题答案'
                }
                if (!$('#new_pwd').val()) {
                    throw '请输入新密码';
                }
            })
            .reduce((arr, item) => (arr.push(item), arr), [])
            .map(data =>
                data.sort(function (a, b) {
                    return a.key > b.key
                })
                    .map(item => item.key + '=' + item.value)
                    .join('&'))
            .flatMap(function (pwd_bck) {
                var newPwd = $('#new_pwd').val();
                return client.setNewPwd({
                    username: $("#username").val(),
                    newPwd: newPwd,
                    pwd_bck: pwd_bck
                })
            })
            .subscribe(
            () => '',
            function (ex) {
                toastr.error(client.IMError[ex.code] || ex, '重置密码');
            },
            function () {
                setTimeout(function () {
                    $('#set_new_pwd').modal('hide');
                }, 800);
                toastr.success('success', '重置密码');
            }
        );
    };

    //重置密码
    $('#xxim_setNewPwd').on('click', function () {
        setNewPwd();
    });

}(window);

