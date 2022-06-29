def get_ds_name(data: dict) -> str:
    d_name = data['result']['file']
    d_name = d_name[d_name.rfind('/') + 1:]
    return d_name[:d_name.find('.')]


def get_exp_name(data: dict) -> str:
    if 'lang' in data['result'] and data['result']['lang'] != 'protobuf':
        e = data['result']['lang']
    elif data['stream_opts']['nameTableSize'] == 256:
        e = 'jelly_noprefix_sm'
    elif data['stream_opts']['prefixTableSize'] == 0:
        e = 'jelly_noprefix'
    elif not data['stream_opts']['useRepeat']:
        e = 'jelly_norepeat'
    else:
        e = 'jelly_full'

    if 'useGzip' in data['result'] and data['result']['useGzip']:
        return e + '_gzip'
    else:
        return e


def get_network_cond(data: dict) -> str:
    if 'port' in data['result']:
        if data['result']['port'] == 8422:
            return '50Mbit'
        elif data['result']['port'] == 8421:
            return '100Mbit'
        else:
            return 'unlimited'

    if '9094' in data['result']['serverProd']:
        return '50Mbit'
    elif '9093' in data['result']['serverProd']:
        return '100Mbit'
    else:
        return 'unlimited'


